package org.project.pagamento;

import org.project.model.StatusPagamento;

import java.math.BigDecimal;

/**
 * O que fazer com um pedido quando chega a situação REAL de um pagamento.
 * Só lógica, sem banco: dá para testar todos os casos chamando decidir() direto.
 */
public enum DecisaoPagamento {

    /** Pagamento aprovado, valor certo, pedido aguardando: marcar como PAGO. */
    CONFIRMAR,
    /** Este mesmo pagamento já confirmou este pedido (aviso repetido): não fazer nada. */
    JA_CONFIRMADO,
    /** Pagamento ainda não aprovado (pendente, recusado...): não mexer no pedido. */
    AGUARDAR,
    /** Aprovado, mas o valor não bate com o total do pedido: NÃO confirmar (suspeito). */
    VALOR_DIVERGENTE,
    /** Pedido já pago por OUTRO pagamento: cliente pagou duas vezes, precisa estornar. */
    PAGO_EM_DOBRO,
    /** Pagamento aprovado para um pedido cancelado: precisa estornar. */
    PEDIDO_CANCELADO,
    /** O pagamento que confirmou o pedido foi estornado/contestado: o pedido vira CANCELADO. */
    ESTORNAR;

    /**
     * @param statusPedido          situação atual do pedido no nosso banco
     * @param totalPedido           total do pedido (do banco)
     * @param pagamentoJaVinculado  id do pagamento que já confirmou o pedido (ou null)
     * @param pagamento             situação real, consultada na API do Mercado Pago
     */
    public static DecisaoPagamento decidir(StatusPagamento statusPedido,
                                           BigDecimal totalPedido,
                                           String pagamentoJaVinculado,
                                           PagamentoMercadoPago pagamento) {

        String idPagamento = String.valueOf(pagamento.id());

        // Este é o pagamento que já confirmou o pedido
        if (idPagamento.equals(pagamentoJaVinculado)) {
            // O dinheiro voltou (estorno ou contestação): o pedido não está mais pago
            if (pagamento.dinheiroDevolvido() && statusPedido == StatusPagamento.PAGO) {
                return ESTORNAR;
            }
            // Aviso repetido: o Mercado Pago reenvia; não é erro
            return JA_CONFIRMADO;
        }

        if (!pagamento.aprovado()) {
            return AGUARDAR;
        }

        if (statusPedido == StatusPagamento.PAGO) {
            return PAGO_EM_DOBRO;
        }
        if (statusPedido == StatusPagamento.CANCELADO) {
            return PEDIDO_CANCELADO;
        }

        // compareTo, e não equals: para BigDecimal, 33.0 e 33.00 NÃO são equals (Aula 3)
        boolean moedaCerta = "BRL".equals(pagamento.currencyId());
        boolean valorCerto = pagamento.transactionAmount() != null
                && pagamento.transactionAmount().compareTo(totalPedido) == 0;
        if (!moedaCerta || !valorCerto) {
            return VALOR_DIVERGENTE;
        }

        return CONFIRMAR;
    }
}
