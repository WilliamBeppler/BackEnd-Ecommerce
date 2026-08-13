package org.project.controller;

import org.project.model.Pedido;
import org.project.model.Produto;
import org.project.repository.PedidoRepository;
import org.project.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")

public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private ProdutoRepository produtoRepository;

    @PostMapping
    public Pedido criarPedido(@RequestBody Pedido novoPedido) {

        // 1. Status inicial
        novoPedido.setStatus("PENDENTE");

        // 2. Associa o usuario (caso tenha vindo do endereço)
        if (novoPedido.getEndereco() != null && novoPedido.getEndereco().getUsuario() != null) {
            novoPedido.setUsuario(novoPedido.getEndereco().getUsuario());
        }

        // 3. REGRA DE NEGÓCIO: Calcula o Valor Total e atrela os itens ao pedido
        BigDecimal totalDaCompra = BigDecimal.ZERO; // Inicia o contador com zero

        if (novoPedido.getItens() != null) {
            for (var item : novoPedido.getItens()) {

                // 1. Pega o ID do produto fantasma que veio do Front-End
                int produtoId = item.getProduto().getId();

                // 2. Busca o Produto REAL no banco de dados!
                Produto produtoReal = produtoRepository.findById(produtoId)
                        .orElseThrow(() -> new RuntimeException("Produto não encontrado no banco de dados!"));

                // 3. Substitui o produto fantasma pelo real
                item.setProduto(produtoReal);

                // Converte a quantidade (int) para BigDecimal para poder multiplicar
                BigDecimal quantidade = BigDecimal.valueOf(item.getQuantidade());

                // Matemática: Preço do item x Quantidade (.multiply)
                BigDecimal subtotalDoItem = item.getPrecoUnitario().multiply(quantidade);

                // Matemática: Adiciona ao Total (.add)
                totalDaCompra = totalDaCompra.add(subtotalDoItem);

                // PREVENÇÃO DE ERRO: Avisa ao Item que ele pertence a este Pedido
                item.setPedido(novoPedido);
            }
        }

        // Guarda o valor total calculado
        novoPedido.setValorTotal(totalDaCompra);

        return pedidoRepository.save(novoPedido);
    }
}
