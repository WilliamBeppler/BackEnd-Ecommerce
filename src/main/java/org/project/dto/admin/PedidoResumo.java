package org.project.dto.admin;

import org.project.model.Pedido;
import org.project.model.StatusPagamento;
import org.project.model.StatusProcessamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Uma linha da lista de pedidos do painel. Só o necessário: nada de CPF, telefone ou endereço. */
public record PedidoResumo(int id,
                           LocalDateTime data,
                           BigDecimal total,
                           String clienteNome,
                           String clienteEmail,
                           String metodoPagamento,
                           StatusPagamento statusPagamento,
                           StatusProcessamento statusProcessamento) {

    public static PedidoResumo de(Pedido pedido) {
        return new PedidoResumo(
                pedido.getId(),
                pedido.getDataPedido(),
                pedido.getValorTotal(),
                pedido.getUsuario().getNome(),
                pedido.getUsuario().getEmail(),
                pedido.getMetodoPagamento(),
                pedido.getStatusPagamento(),
                pedido.getStatusProcessamento());
    }
}
