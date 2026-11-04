package org.project.pagamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * As duas portas por onde chega a notícia de um pagamento, e o que cada uma confere
 * ANTES de entregar ao ProcessadorPagamento:
 *
 *  1. receberAviso: o webhook (servidor do Mercado Pago → nosso servidor).
 *     Confere a ASSINATURA e depois consulta o pagamento na API.
 *  2. conferirPeloRetorno: a página de volta da loja (navegador do cliente → nosso servidor).
 *     Não há assinatura (vem do navegador), então confere que o pagamento consultado na API
 *     é MESMO deste pedido. Existe porque pagamentos de teste não disparam o webhook, e
 *     porque um webhook atrasado não deve deixar o cliente esperando.
 *
 * Nas duas portas, o que decide é a consulta à API do Mercado Pago, nunca o que chegou.
 */
@Service
public class ConfirmacaoPagamento {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoPagamento.class);

    public enum ResultadoAviso { ACEITO, ASSINATURA_INVALIDA, ERRO_TEMPORARIO }

    private final MercadoPagoConfiguracao config;
    private final MercadoPagoCliente mercadoPago;
    private final ProcessadorPagamento processador;

    public ConfirmacaoPagamento(MercadoPagoConfiguracao config, MercadoPagoCliente mercadoPago,
                                ProcessadorPagamento processador) {
        this.config = config;
        this.mercadoPago = mercadoPago;
        this.processador = processador;
    }

    /**
     * Webhook. Responde ACEITO para tudo que não for erro nosso — inclusive avisos que
     * ignoramos (outro tipo de evento, pagamento inexistente) —, senão o Mercado Pago
     * fica reenviando por dias. ERRO_TEMPORARIO (Mercado Pago fora do ar) faz ele tentar de novo.
     */
    public ResultadoAviso receberAviso(String xSignature, String xRequestId, String dataId, String tipo) {
        // 1. Veio mesmo do Mercado Pago? Sem assinatura válida, nem olhamos o resto.
        if (!AssinaturaWebhook.valida(xSignature, xRequestId, dataId, config.getWebhookSecret())) {
            // O x-signature não é segredo (é o resultado da conta, não a chave): ajuda a diagnosticar
            log.warn("Aviso com assinatura INVÁLIDA recusado (data.id={}, type={}, request-id={}, x-signature={}).",
                    dataId, tipo, xRequestId, xSignature);
            return ResultadoAviso.ASSINATURA_INVALIDA;
        }

        // 2. Só nos interessam avisos de pagamento (há outros tipos de evento)
        if (!"payment".equals(tipo)) {
            log.info("Aviso do tipo '{}' ignorado.", tipo);
            return ResultadoAviso.ACEITO;
        }

        // 3. Pergunta ao Mercado Pago a situação REAL (o aviso só diz "o pagamento X mudou")
        try {
            Optional<PagamentoMercadoPago> pagamento = mercadoPago.buscarPagamento(dataId);
            if (pagamento.isEmpty()) {
                log.info("Aviso do pagamento {}, que não existe na API (ex.: notificação simulada); ignorado.", dataId);
                return ResultadoAviso.ACEITO;
            }
            processador.processar(pagamento.get());
            return ResultadoAviso.ACEITO;

        } catch (PagamentoException e) {
            log.error("Não foi possível consultar o pagamento {} agora; o Mercado Pago vai reenviar o aviso.", dataId);
            return ResultadoAviso.ERRO_TEMPORARIO;
        }
    }

    public void conferirPeloRetorno(String paymentId, int pedidoId) {
        try {
            mercadoPago.buscarPagamento(paymentId).ifPresent(pagamento -> {
                if (String.valueOf(pedidoId).equals(pagamento.externalReference())) {
                    processador.processar(pagamento);
                } else {
                    log.warn("Retorno do pedido {} trouxe o pagamento {}, que é de outro pedido ({}); ignorado.",
                            pedidoId, paymentId, pagamento.externalReference());
                }
            });
        } catch (PagamentoException e) {
            // Sem problema: a página continua perguntando, e o webhook também confirma
            log.warn("Não foi possível conferir o pagamento {} do pedido {} agora.", paymentId, pedidoId);
        }
    }
}
