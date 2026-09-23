package org.project.controller;

import org.project.model.Pedido;
import org.project.model.Produto;
import org.project.model.Usuario;
import org.project.repository.PedidoRepository;
import org.project.repository.ProdutoRepository;
import org.project.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping
    public ResponseEntity<?> criarPedido(@RequestBody Pedido novoPedido,
                                         Authentication authentication) {

        // CORREÇÃO DE SEGURANÇA: o dono do pedido vem do TOKEN, não do body.
        String emailLogado = authentication.getName();
        Usuario usuarioLogado = usuarioRepository.findByEmail(emailLogado);

        if (usuarioLogado == null) {
            return ResponseEntity.status(401).body("Erro: usuário do token não existe mais.");
        }

        novoPedido.setUsuario(usuarioLogado);

        if (novoPedido.getEndereco() != null) {
            novoPedido.getEndereco().setUsuario(usuarioLogado);
        }

        novoPedido.setStatus("PENDENTE");

        // REGRA DE NEGÓCIO: Calcula o Valor Total e atrela os itens ao pedido
        BigDecimal totalDaCompra = BigDecimal.ZERO;

        if (novoPedido.getItens() != null) {
            for (var item : novoPedido.getItens()) {

                int produtoId = item.getProduto().getId();

                Produto produtoReal = produtoRepository.findById(produtoId)
                        .orElseThrow(() -> new RuntimeException("Produto não encontrado no banco de dados!"));

                item.setProduto(produtoReal);

                BigDecimal precoReal = BigDecimal.valueOf(produtoReal.getPreco());
                item.setPrecoUnitario(precoReal);

                BigDecimal quantidade = BigDecimal.valueOf(item.getQuantidade());
                BigDecimal subtotalDoItem = precoReal.multiply(quantidade);

                totalDaCompra = totalDaCompra.add(subtotalDoItem);

                item.setPedido(novoPedido);
            }
        }

        novoPedido.setValorTotal(totalDaCompra);

        return ResponseEntity.ok(pedidoRepository.save(novoPedido));
    }
}