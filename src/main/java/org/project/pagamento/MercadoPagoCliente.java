package org.project.pagamento;

import org.project.model.ItemPedido;
import org.project.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A única classe que conversa com a API do Mercado Pago. Faz duas coisas:
 *  1. criarPreferencia: monta a cobrança de um pedido e recebe o link de pagamento;
 *  2. buscarPagamento: pergunta ao Mercado Pago a situação REAL de um pagamento.
 *
 * Chamadas feitas "na mão" com o RestClient do Spring (sem o SDK do Mercado Pago),
 * para dar para ver cada requisição: endereço, cabeçalho e JSON.
 */
@Component
public class MercadoPagoCliente {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoCliente.class);

    private static final String MOEDA = "BRL";
    // O que aparece na fatura do cartão do cliente (o Mercado Pago aceita até 22 caracteres)
    private static final String NOME_NA_FATURA = "PEDACINHODOCEU";
    // Página da loja que recebe o cliente de volta (criada na Aula 5)
    private static final String PAGINA_DE_RETORNO = "/pages/pagamento-retorno.html?situacao=";
    // Id de pagamento do Mercado Pago: só dígitos. Qualquer outra coisa é recusada antes de virar URL.
    private static final Pattern ID_PAGAMENTO = Pattern.compile("^\\d{1,20}$");

    private final RestClient http;
    private final MercadoPagoConfiguracao config;

    public MercadoPagoCliente(RestClient.Builder builder, MercadoPagoConfiguracao config) {
        this.config = config;

        // Tempo máximo de espera. Sem isso, se o Mercado Pago travar, o checkout do
        // cliente ficaria "carregando" para sempre, segurando uma conexão do servidor.
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));   // para conectar
        fabrica.setReadTimeout(Duration.ofSeconds(15));     // para receber a resposta

        this.http = builder
                .baseUrl(config.getApiUrl())                 // https://api.mercadopago.com
                .requestFactory(fabrica)
                // Toda chamada leva o token. Ele fica só aqui dentro: nunca em log.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAccessToken())
                .build();
    }

    /**
     * Cria a cobrança (preferência) de um pedido JÁ SALVO no banco.
     * Os preços vêm dos itens do pedido, que vieram do banco — nunca do navegador.
     * O notificationUrl pode ser null (o webhook só existe a partir da Aula 6).
     */
    public PreferenciaResposta criarPreferencia(Pedido pedido, String notificationUrl) {
        List<PreferenciaRequest.Item> itens = pedido.getItens().stream()
                .map(this::paraItemMercadoPago)
                .toList();

        String retorno = config.getLojaUrl() + PAGINA_DE_RETORNO;

        // Voltar sozinho para a loja quando aprovado ("approved") só funciona com endereço
        // PÚBLICO. Com localhost o Mercado Pago recusa a preferência inteira (erro 400
        // invalid_auto_return); por isso, em desenvolvimento, o campo não é enviado
        // (null some do JSON) e o cliente volta pelo botão "Voltar ao site".
        String autoReturn = config.lojaEmEnderecoLocal() ? null : "approved";

        PreferenciaRequest corpo = new PreferenciaRequest(
                itens,
                new PreferenciaRequest.Payer(pedido.getUsuario().getEmail()),
                new PreferenciaRequest.BackUrls(retorno + "sucesso", retorno + "pendente", retorno + "falha"),
                autoReturn,
                notificationUrl,
                String.valueOf(pedido.getId()),      // o "elo" entre o pagamento e o NOSSO pedido
                NOME_NA_FATURA);

        try {
            PreferenciaResposta resposta = http.post()
                    .uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(PreferenciaResposta.class);

            if (resposta == null || resposta.id() == null) {
                throw new PagamentoException("O Mercado Pago respondeu sem o id da preferência (pedido " + pedido.getId() + ").", null);
            }

            log.info("Preferência {} criada para o pedido {}", resposta.id(), pedido.getId());
            return resposta;

        } catch (RestClientResponseException e) {
            // O Mercado Pago respondeu, mas com erro (ex.: 400 = algum campo inválido).
            // O corpo do erro explica o motivo e não tem segredo: vai para o log.
            log.error("Mercado Pago recusou a preferência do pedido {}: {} {}",
                    pedido.getId(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new PagamentoException("Mercado Pago recusou a preferência (HTTP " + e.getStatusCode().value() + ").", e);

        } catch (ResourceAccessException e) {
            // Nem chegou resposta: sem internet, fora do ar ou passou do tempo máximo
            log.error("Sem resposta do Mercado Pago ao criar a preferência do pedido {}: {}", pedido.getId(), e.getMessage());
            throw new PagamentoException("Mercado Pago indisponível.", e);
        }
    }

    /**
     * Consulta a situação real de um pagamento. Vazio se o pagamento não existir.
     * O id vem de fora (do webhook): por isso é conferido antes de virar parte da URL.
     */
    public Optional<PagamentoMercadoPago> buscarPagamento(String idPagamento) {
        if (idPagamento == null || !ID_PAGAMENTO.matcher(idPagamento).matches()) {
            log.warn("Id de pagamento com formato inválido recebido; ignorado.");
            return Optional.empty();
        }

        try {
            PagamentoMercadoPago pagamento = http.get()
                    .uri("/v1/payments/{id}", idPagamento)   // {id} é preenchido com segurança (escapado)
                    .retrieve()
                    .body(PagamentoMercadoPago.class);
            return Optional.ofNullable(pagamento);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.warn("Pagamento {} não existe no Mercado Pago.", idPagamento);
                return Optional.empty();
            }
            log.error("Erro ao consultar o pagamento {}: {} {}", idPagamento, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PagamentoException("Erro ao consultar pagamento no Mercado Pago.", e);

        } catch (ResourceAccessException e) {
            log.error("Sem resposta do Mercado Pago ao consultar o pagamento {}: {}", idPagamento, e.getMessage());
            throw new PagamentoException("Mercado Pago indisponível.", e);
        }
    }

    private PreferenciaRequest.Item paraItemMercadoPago(ItemPedido item) {
        return new PreferenciaRequest.Item(
                String.valueOf(item.getProduto().getId()),
                item.getProduto().getNome(),
                item.getQuantidade(),
                MOEDA,
                item.getPrecoUnitario());   // BigDecimal: vai no JSON como 12.50, sem erro de centavo
    }
}
