package org.project.controller;

import org.project.dto.PedidoRequest;
import org.project.dto.PedidoRequest.EnderecoRequest;
import org.project.dto.PedidoRequest.ItemRequest;
import org.project.model.Endereco;
import org.project.model.ItemPedido;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    // Mesmo limite do botão de quantidade do carrinho (carrinho.js)
    private static final int QUANTIDADE_MINIMA = 1;
    private static final int QUANTIDADE_MAXIMA = 99;

    // Valores que o checkout oferece (checkout.html)
    private static final Set<String> METODOS_DE_PAGAMENTO = Set.of("PIX", "CARTAO_CREDITO");

    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping
    public ResponseEntity<?> criarPedido(@RequestBody PedidoRequest dados,
                                         Authentication authentication) {

        // CORREÇÃO DE SEGURANÇA: o dono do pedido vem do TOKEN, não do body.
        String emailLogado = authentication.getName();
        Usuario usuarioLogado = usuarioRepository.findByEmail(emailLogado);

        if (usuarioLogado == null) {
            return ResponseEntity.status(401).body("Erro: usuário do token não existe mais.");
        }

        if (dados == null) {
            return ResponseEntity.badRequest().body("Erro: dados do pedido não enviados.");
        }

        if (dados.metodoPagamento() == null || !METODOS_DE_PAGAMENTO.contains(dados.metodoPagamento())) {
            return ResponseEntity.badRequest().body("Erro: forma de pagamento inválida.");
        }

        String erroEndereco = validarEndereco(dados.endereco());
        if (erroEndereco != null) {
            return ResponseEntity.badRequest().body(erroEndereco);
        }

        if (dados.itens() == null || dados.itens().isEmpty()) {
            return ResponseEntity.badRequest().body("Erro: o pedido não tem itens.");
        }

        // Pedido, endereço e itens são montados AQUI, do zero (id = 0 → INSERT).
        // Antes, o JSON preenchia as entidades direto; um "id" de pedido ou de endereço.
        Pedido novoPedido = new Pedido();
        novoPedido.setUsuario(usuarioLogado);
        novoPedido.setStatus("PENDENTE");
        novoPedido.setMetodoPagamento(dados.metodoPagamento());
        novoPedido.setEndereco(montarEndereco(dados.endereco(), usuarioLogado));

        // REGRA DE NEGÓCIO: Calcula o Valor Total e atrela os itens ao pedido
        BigDecimal totalDaCompra = BigDecimal.ZERO;
        List<ItemPedido> itens = new ArrayList<>();

        for (ItemRequest itemPedido : dados.itens()) {

            // O cliente só escolhe O QUÊ e QUANTOS; os dois são conferidos aqui
            if (itemPedido == null || itemPedido.produtoId() == null) {
                return ResponseEntity.badRequest().body("Erro: item do pedido sem produto.");
            }

            // Sem isso, quantidade negativa geraria subtotal negativo e baixaria o total
            Integer quantidadePedida = itemPedido.quantidade();
            if (quantidadePedida == null || quantidadePedida < QUANTIDADE_MINIMA || quantidadePedida > QUANTIDADE_MAXIMA) {
                return ResponseEntity.badRequest().body(
                        "Erro: quantidade inválida. Use de " + QUANTIDADE_MINIMA + " a " + QUANTIDADE_MAXIMA + ".");
            }

            int produtoId = itemPedido.produtoId();
            Produto produtoReal = produtoRepository.findById(produtoId).orElse(null);
            if (produtoReal == null) {
                return ResponseEntity.badRequest().body("Erro: produto " + produtoId + " não encontrado.");
            }

            // Desativado no painel, mas ainda no carrinho (localStorage) de alguém
            if (!produtoReal.isAtivo()) {
                return ResponseEntity.badRequest().body(
                        "Erro: o produto \"" + produtoReal.getNome() + "\" não está mais disponível. Remova-o do carrinho.");
            }

            // Preço vem do banco (já em BigDecimal), nunca do que o navegador mandou
            BigDecimal precoReal = produtoReal.getPreco();
            BigDecimal subtotalDoItem = precoReal.multiply(BigDecimal.valueOf(quantidadePedida));
            totalDaCompra = totalDaCompra.add(subtotalDoItem);

            ItemPedido item = new ItemPedido();
            item.setProduto(produtoReal);
            item.setQuantidade(quantidadePedida);
            item.setPrecoUnitario(precoReal);
            item.setPedido(novoPedido);
            itens.add(item);
        }

        novoPedido.setItens(itens);
        novoPedido.setValorTotal(totalDaCompra);

        return ResponseEntity.ok(pedidoRepository.save(novoPedido));
    }

    private Endereco montarEndereco(EnderecoRequest dados, Usuario dono) {
        Endereco endereco = new Endereco();
        endereco.setCep(dados.cep().trim());
        endereco.setRua(dados.rua().trim());
        endereco.setNumero(dados.numero().trim());
        endereco.setComplemento(vazio(dados.complemento()) ? null : dados.complemento().trim());
        endereco.setBairro(dados.bairro().trim());
        endereco.setCidade(dados.cidade().trim());
        endereco.setEstado(dados.estado().trim().toUpperCase());
        endereco.setUsuario(dono);
        return endereco;
    }

    /** Limites iguais aos da tabela enderecos. Devolve a mensagem do problema, ou null. */
    private String validarEndereco(EnderecoRequest e) {
        if (e == null) {
            return "Erro: informe o endereço de entrega.";
        }
        String digitosCep = e.cep() == null ? "" : e.cep().replaceAll("\\D", "");
        if (digitosCep.length() != 8 || e.cep().trim().length() > 9) {
            return "Erro: informe um CEP válido.";
        }
        if (vazio(e.rua()) || e.rua().trim().length() > 150) {
            return "Erro: informe a rua (até 150 caracteres).";
        }
        if (vazio(e.numero()) || e.numero().trim().length() > 20) {
            return "Erro: informe o número (até 20 caracteres).";
        }
        if (!vazio(e.complemento()) && e.complemento().trim().length() > 100) {
            return "Erro: o complemento pode ter no máximo 100 caracteres.";
        }
        if (vazio(e.bairro()) || e.bairro().trim().length() > 100) {
            return "Erro: informe o bairro (até 100 caracteres).";
        }
        if (vazio(e.cidade()) || e.cidade().trim().length() > 100) {
            return "Erro: informe a cidade (até 100 caracteres).";
        }
        if (e.estado() == null || !e.estado().trim().matches("[A-Za-z]{2}")) {
            return "Erro: informe o estado com 2 letras (ex.: SC).";
        }
        return null;
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
