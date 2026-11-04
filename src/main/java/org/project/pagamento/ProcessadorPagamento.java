package org.project.pagamento;

import org.project.model.Pedido;
import org.project.model.PedidoHistorico;
import org.project.model.StatusPagamento;
import org.project.repository.PedidoHistoricoRepository;
import org.project.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * O ÚNICO lugar que muda um pedido por causa de um pagamento do Mercado Pago.
 * Usado pelo webhook e pela conferência da página de volta: os dois caminhos
 * chegam aqui com a situação REAL do pagamento (consultada na API), nunca com
 * o que veio no aviso ou no endereço.
 */
@Service
public class ProcessadorPagamento {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorPagamento.class);

    static final String AUTOR_GATEWAY = "gateway:mercadopago";

    private final PedidoRepository pedidoRepository;
    private final PedidoHistoricoRepository historicoRepository;

    public ProcessadorPagamento(PedidoRepository pedidoRepository, PedidoHistoricoRepository historicoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.historicoRepository = historicoRepository;
    }

    @Transactional
    public DecisaoPagamento processar(PagamentoMercadoPago pagamento) {
        String referencia = pagamento.externalReference();
        if (referencia == null || !referencia.matches("\\d{1,9}")) {
            log.warn("Pagamento {} sem número de pedido válido (external_reference); ignorado.", pagamento.id());
            return DecisaoPagamento.AGUARDAR;
        }

        Pedido pedido = pedidoRepository.buscarParaAtualizar(Integer.parseInt(referencia)).orElse(null);
        if (pedido == null) {
            log.warn("Pagamento {} aponta para o pedido {}, que não existe; ignorado.", pagamento.id(), referencia);
            return DecisaoPagamento.AGUARDAR;
        }

        DecisaoPagamento decisao = DecisaoPagamento.decidir(
                pedido.getStatusPagamento(), pedido.getValorTotal(), pedido.getGatewayPagamentoId(), pagamento);

        switch (decisao) {
            case CONFIRMAR -> confirmar(pedido, pagamento);
            case ESTORNAR -> estornar(pedido, pagamento);
            case JA_CONFIRMADO, AGUARDAR ->
                    log.info("Pagamento {} (status {}) do pedido {}: nada a mudar.", pagamento.id(), pagamento.status(), pedido.getId());

            case VALOR_DIVERGENTE -> log.error(
                    "ATENÇÃO: pagamento {} aprovado com valor {} {} para o pedido {} de total {}. NÃO confirmado — verificar.",
                    pagamento.id(), pagamento.transactionAmount(), pagamento.currencyId(), pedido.getId(), pedido.getValorTotal());
            case PAGO_EM_DOBRO -> log.error(
                    "ATENÇÃO: pedido {} já estava pago pelo pagamento {}, e o pagamento {} também foi aprovado. Estornar o duplicado no painel do Mercado Pago.",
                    pedido.getId(), pedido.getGatewayPagamentoId(), pagamento.id());
            case PEDIDO_CANCELADO -> log.error(
                    "ATENÇÃO: pagamento {} aprovado para o pedido {}, que está CANCELADO. Estornar no painel do Mercado Pago.",
                    pagamento.id(), pedido.getId());
        }
        return decisao;
    }

    private void confirmar(Pedido pedido, PagamentoMercadoPago pagamento) {
        StatusPagamento anterior = pedido.getStatusPagamento();
        pedido.setStatusPagamento(StatusPagamento.PAGO);
        pedido.setGatewayPagamentoId(String.valueOf(pagamento.id()));
        pedido.setDataPagamento(LocalDateTime.now());
        pedido.setMetodoPagamento(pagamento.formaDePagamento()); // MERCADO_PAGO vira PIX, CARTAO_CREDITO...
        pedidoRepository.save(pedido);

        historicoRepository.save(new PedidoHistorico(pedido, "PAGAMENTO", anterior.name(),
                StatusPagamento.PAGO.name(), AUTOR_GATEWAY, LocalDateTime.now()));
        log.info("Pedido {} PAGO pelo pagamento {} ({}).", pedido.getId(), pagamento.id(), pedido.getMetodoPagamento());
    }

    private void estornar(Pedido pedido, PagamentoMercadoPago pagamento) {
        StatusPagamento anterior = pedido.getStatusPagamento();
        pedido.setStatusPagamento(StatusPagamento.CANCELADO);
        pedidoRepository.save(pedido);

        historicoRepository.save(new PedidoHistorico(pedido, "PAGAMENTO", anterior.name(),
                StatusPagamento.CANCELADO.name(), AUTOR_GATEWAY, LocalDateTime.now()));
        // Se já tinha sido enviado, alguém precisa resolver com o cliente
        log.warn("Pedido {} CANCELADO: o pagamento {} ficou como '{}' (dinheiro devolvido ao cliente). Processamento estava: {}.",
                pedido.getId(), pagamento.id(), pagamento.status(), pedido.getStatusProcessamento());
    }
}
