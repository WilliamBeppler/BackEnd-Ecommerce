package org.project.dto;

import org.project.model.ItemPedido;
import org.project.model.Pedido;
import org.project.model.StatusPagamento;
import org.project.model.StatusProcessamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Um pedido na lista "Meus pedidos" do perfil do cliente.
 * Só o que o próprio cliente precisa ver: nada de endereço, CPF ou dados do Mercado Pago.
 */
public record MeuPedido(int id,
                        LocalDateTime data,
                        BigDecimal total,
                        StatusPagamento statusPagamento,
                        StatusProcessamento statusProcessamento,
                        String codigoRastreio,
                        List<Item> itens) {

    public record Item(String produto, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {

        static Item de(ItemPedido item) {
            // Preço unitário gravado no pedido (o da época da compra), não o preço atual do produto
            BigDecimal subtotal = item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));
            return new Item(item.getProduto().getNome(), item.getQuantidade(), item.getPrecoUnitario(), subtotal);
        }
    }

    public static MeuPedido de(Pedido pedido) {
        return new MeuPedido(
                pedido.getId(),
                pedido.getDataPedido(),
                pedido.getValorTotal(),
                pedido.getStatusPagamento(),
                pedido.getStatusProcessamento(),
                pedido.getCodigoRastreio(),
                pedido.getItens().stream().map(Item::de).toList());
    }
}
