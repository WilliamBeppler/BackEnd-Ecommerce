package org.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta do checkout para o navegador: o número do pedido e o link de pagamento
 * do Mercado Pago. Se o Mercado Pago falhar, vem "erro" (e o pedido continua salvo,
 * aguardando pagamento, para o cliente tentar de novo sem criar outro pedido).
 *
 * Antes, o checkout devolvia a entidade Pedido inteira; agora só o que o front precisa.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagamentoIniciado(int pedidoId, String linkPagamento, String erro) {

    public static PagamentoIniciado comLink(int pedidoId, String link) {
        return new PagamentoIniciado(pedidoId, link, null);
    }

    public static PagamentoIniciado comErro(int pedidoId, String erro) {
        return new PagamentoIniciado(pedidoId, null, erro);
    }
}
