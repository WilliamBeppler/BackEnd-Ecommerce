package org.project.dto;

import java.util.List;

/**
 * O que o checkout pode mandar. O cliente diz O QUÊ quer (produtoId), QUANTOS e PARA ONDE.
 *
 * Não há id de pedido, de endereço nem de item: o servidor sempre cria registros novos,
 * então ninguém consegue sobrescrever o pedido ou o endereço de outro cliente.
 * Dono, preço, total e status também não estão aqui: são sempre decididos pelo servidor.
 */
public record PedidoRequest(EnderecoRequest endereco,
                            String metodoPagamento,
                            List<ItemRequest> itens) {

    public record EnderecoRequest(String cep,
                                  String rua,
                                  String numero,
                                  String complemento,
                                  String bairro,
                                  String cidade,
                                  String estado) {
    }

    /** produtoId é legítimo: é como o cliente escolhe o produto (e o servidor confere no banco). */
    public record ItemRequest(Integer produtoId, Integer quantidade) {
    }
}
