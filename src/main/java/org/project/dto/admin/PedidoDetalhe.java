package org.project.dto.admin;

import org.project.model.*;
import org.project.service.RegrasPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tudo que a tela de detalhe do pedido mostra.
 * Montado de propósito, campo por campo: os dados pessoais (CPF, telefone, endereço)
 * só saem por aqui, numa rota de ADMIN com trava por Origin — nunca "vazando" pela entidade.
 */
public record PedidoDetalhe(int id,
                            LocalDateTime data,
                            BigDecimal total,
                            String metodoPagamento,
                            StatusPagamento statusPagamento,
                            StatusProcessamento statusProcessamento,
                            String codigoRastreio,
                            Comprador comprador,
                            EnderecoEntrega endereco,
                            List<Item> itens,
                            List<Historico> historico,
                            List<String> acoesPermitidas) {

    public record Comprador(String nome, String email, String telefone, String cpf) {
    }

    public record EnderecoEntrega(String cep, String rua, String numero, String complemento,
                                  String bairro, String cidade, String estado) {
    }

    public record Item(int produtoId, String nome, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {
    }

    public record Historico(String campo, String de, String para, String adminEmail, LocalDateTime data) {
    }

    public static PedidoDetalhe de(Pedido pedido, List<PedidoHistorico> historico) {
        Usuario u = pedido.getUsuario();
        Endereco e = pedido.getEndereco();

        List<Item> itens = pedido.getItens() == null ? List.of() : pedido.getItens().stream()
                .map(item -> new Item(
                        item.getProduto().getId(),
                        item.getProduto().getNome(),
                        item.getQuantidade(),
                        item.getPrecoUnitario(), // o preço da época da compra, não o de hoje
                        item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()))))
                .toList();

        List<Historico> linhas = historico.stream()
                .map(h -> new Historico(h.getCampo(), h.getDe(), h.getPara(), h.getAdminEmail(), h.getData()))
                .toList();

        return new PedidoDetalhe(
                pedido.getId(),
                pedido.getDataPedido(),
                pedido.getValorTotal(),
                pedido.getMetodoPagamento(),
                pedido.getStatusPagamento(),
                pedido.getStatusProcessamento(),
                pedido.getCodigoRastreio(),
                new Comprador(u.getNome(), u.getEmail(), u.getTelefone(), u.getCpf()),
                e == null ? null : new EnderecoEntrega(e.getCep(), e.getRua(), e.getNumero(), e.getComplemento(),
                        e.getBairro(), e.getCidade(), e.getEstado()),
                itens,
                linhas,
                RegrasPedido.acoesPermitidas(pedido.getStatusPagamento(), pedido.getStatusProcessamento()));
    }
}
