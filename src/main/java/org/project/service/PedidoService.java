package org.project.service;

import org.project.dto.MeuPedido;
import org.project.dto.PagamentoIniciado;
import org.project.dto.PedidoRequest;
import org.project.dto.PedidoRequest.EnderecoRequest;
import org.project.dto.PedidoRequest.ItemRequest;
import org.project.dto.SituacaoPedido;
import org.project.model.Endereco;
import org.project.model.ItemPedido;
import org.project.model.Pedido;
import org.project.model.Produto;
import org.project.model.StatusPagamento;
import org.project.model.Usuario;
import org.project.pagamento.ConfirmacaoPagamento;
import org.project.pagamento.MercadoPagoCliente;
import org.project.pagamento.PagamentoException;
import org.project.pagamento.PreferenciaResposta;
import org.project.repository.PedidoRepository;
import org.project.repository.ProdutoRepository;
import org.project.service.excecao.NaoEncontradoException;
import org.project.service.excecao.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoService {

    // Mesmo limite do botão de quantidade do carrinho (carrinho.js)
    private static final int QUANTIDADE_MINIMA = 1;
    private static final int QUANTIDADE_MAXIMA = 99;

    // O cliente escolhe PIX, cartão ou boleto na página do Mercado Pago, não aqui.
    // O webhook troca este valor pela forma de pagamento que ele usou de verdade.
    private static final String METODO_MERCADO_PAGO = "MERCADO_PAGO";

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioService usuarioService;
    private final MercadoPagoCliente mercadoPago;
    private final ConfirmacaoPagamento confirmacaoPagamento;

    public PedidoService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
                         UsuarioService usuarioService, MercadoPagoCliente mercadoPago,
                         ConfirmacaoPagamento confirmacaoPagamento) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.usuarioService = usuarioService;
        this.mercadoPago = mercadoPago;
        this.confirmacaoPagamento = confirmacaoPagamento;
    }

    public Pedido criar(String emailLogado, PedidoRequest dados) {
        Usuario usuarioLogado = usuarioService.buscarLogado(emailLogado);

        if (dados == null) {
            throw new RegraDeNegocioException("Erro: dados do pedido não enviados.");
        }

        String erroEndereco = ValidadorPedido.validarEndereco(dados.endereco());
        if (erroEndereco != null) {
            throw new RegraDeNegocioException(erroEndereco);
        }

        if (dados.itens() == null || dados.itens().isEmpty()) {
            throw new RegraDeNegocioException("Erro: o pedido não tem itens.");
        }


        Pedido novoPedido = new Pedido();
        novoPedido.setUsuario(usuarioLogado);
        novoPedido.setStatus("PENDENTE");
        novoPedido.setMetodoPagamento(METODO_MERCADO_PAGO);
        novoPedido.setEndereco(montarEndereco(dados.endereco(), usuarioLogado));

        // REGRA DE NEGÓCIO: Calcula o Valor Total e atrela os itens ao pedido
        BigDecimal totalDaCompra = BigDecimal.ZERO;
        List<ItemPedido> itens = new ArrayList<>();

        for (ItemRequest itemPedido : dados.itens()) {
            if (itemPedido == null || itemPedido.produtoId() == null) {
                throw new RegraDeNegocioException("Erro: item do pedido sem produto.");
            }

            Integer quantidadePedida = itemPedido.quantidade();
            if (quantidadePedida == null || quantidadePedida < QUANTIDADE_MINIMA || quantidadePedida > QUANTIDADE_MAXIMA) {
                throw new RegraDeNegocioException(
                        "Erro: quantidade inválida. Use de " + QUANTIDADE_MINIMA + " a " + QUANTIDADE_MAXIMA + ".");
            }

            int produtoId = itemPedido.produtoId();
            Produto produtoReal = produtoRepository.findById(produtoId).orElse(null);
            if (produtoReal == null) {
                throw new RegraDeNegocioException("Erro: produto " + produtoId + " não encontrado.");
            }

            // Desativado no painel, mas ainda no carrinho (localStorage) de alguém
            if (!produtoReal.isAtivo()) {
                throw new RegraDeNegocioException(
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

        return pedidoRepository.save(novoPedido);
    }


    public PagamentoIniciado iniciarPagamento(Pedido pedido) {
        try {
            PreferenciaResposta preferencia = mercadoPago.criarPreferencia(pedido, null);
            return PagamentoIniciado.comLink(pedido.getId(), preferencia.initPoint());

        } catch (PagamentoException e) {

            log.warn("Pedido {} salvo, mas sem link de pagamento: {}", pedido.getId(), e.getMessage());
            return PagamentoIniciado.comErro(pedido.getId(),
                    "Não foi possível abrir o pagamento agora. Seu pedido foi guardado; tente novamente em instantes.");
        }
    }

    public PagamentoIniciado pagarNovamente(String emailLogado, int id) {
        Pedido pedido = buscarDoCliente(emailLogado, pedidoRepository.buscarComItens(id).orElse(null));

        if (pedido.getStatusPagamento() != StatusPagamento.AGUARDANDO) {
            throw new RegraDeNegocioException("Erro: este pedido não está aguardando pagamento.");
        }
        return iniciarPagamento(pedido);
    }

    public List<MeuPedido> meusPedidos(String emailLogado) {
        Usuario usuarioLogado = usuarioService.buscarLogado(emailLogado);
        return pedidoRepository.buscarDoClienteComItens(usuarioLogado.getId()).stream()
                .map(MeuPedido::de)
                .toList();
    }

    public SituacaoPedido situacao(String emailLogado, int id) {
        Pedido pedido = buscarDoCliente(emailLogado, pedidoRepository.findById(id).orElse(null));
        return new SituacaoPedido(pedido.getId(), pedido.getStatusPagamento());
    }


    public SituacaoPedido conferirPagamento(String emailLogado, int id, String paymentId) {
        Pedido pedido = buscarDoCliente(emailLogado, pedidoRepository.findById(id).orElse(null));

        if (pedido.getStatusPagamento() == StatusPagamento.AGUARDANDO
                || paymentId.equals(pedido.getGatewayPagamentoId())) {
            confirmacaoPagamento.conferirPeloRetorno(paymentId, id);
        }

        Pedido atualizado = pedidoRepository.findById(id).orElse(pedido);
        return new SituacaoPedido(atualizado.getId(), atualizado.getStatusPagamento());
    }

    private Pedido buscarDoCliente(String emailLogado, Pedido pedido) {
        Usuario usuarioLogado = usuarioService.buscarLogado(emailLogado);
        if (pedido == null || pedido.getUsuario().getId() != usuarioLogado.getId()) {
            throw new NaoEncontradoException("Erro: pedido não encontrado.");
        }
        return pedido;
    }

    private Endereco montarEndereco(EnderecoRequest dados, Usuario dono) {
        Endereco endereco = new Endereco();
        endereco.setCep(dados.cep().trim());
        endereco.setRua(dados.rua().trim());
        endereco.setNumero(dados.numero().trim());
        endereco.setComplemento(ValidadorPedido.vazio(dados.complemento()) ? null : dados.complemento().trim());
        endereco.setBairro(dados.bairro().trim());
        endereco.setCidade(dados.cidade().trim());
        endereco.setEstado(dados.estado().trim().toUpperCase());
        endereco.setUsuario(dono);
        return endereco;
    }
}
