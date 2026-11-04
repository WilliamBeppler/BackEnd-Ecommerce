package org.project.dto;

import org.project.model.StatusPagamento;

/**
 * O que o cliente pode saber sobre o próprio pedido na página de volta do pagamento:
 * só o número e se o pagamento já foi confirmado. Nada de endereço, itens ou dados pessoais.
 */
public record SituacaoPedido(int pedidoId, StatusPagamento statusPagamento) {
}
