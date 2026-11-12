package org.project.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.dto.MeuPedido;
import org.project.dto.PagamentoIniciado;
import org.project.dto.PedidoRequest;
import org.project.dto.PedidoRequest.EnderecoRequest;
import org.project.dto.PedidoRequest.ItemRequest;
import org.project.dto.SituacaoPedido;
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
import org.project.service.excecao.NaoAutenticadoException;
import org.project.service.excecao.NaoEncontradoException;
import org.project.service.excecao.RegraDeNegocioException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    private static final String EMAIL = "maria@email.com";

    @Mock PedidoRepository pedidoRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock UsuarioService usuarioService;
    @Mock MercadoPagoCliente mercadoPago;
    @Mock ConfirmacaoPagamento confirmacaoPagamento;

    @InjectMocks PedidoService pedidoService;

    //dados de apoio

    private static Usuario usuario(int id, String email) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail(email);
        u.setNome("Cliente " + id);
        return u;
    }

    private static Produto produto(int id, String nome, String preco, boolean ativo) {
        Produto p = new Produto(id, nome, new BigDecimal(preco), "../imgs/a.jpeg");
        p.setAtivo(ativo);
        return p;
    }

    private static EnderecoRequest enderecoValido() {
        return new EnderecoRequest
                (" 88000-000 ", " Rua das Flores ", " 10 ", "  ",
                        " Centro ", " Florianópolis ", " sc ");
    }

    private static PedidoRequest pedidoCom(ItemRequest... itens) {
        return new PedidoRequest(enderecoValido(), Arrays.asList(itens));
    }

    private static Pedido pedidoDe(Usuario dono, int id, StatusPagamento status) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setUsuario(dono);
        p.setStatusPagamento(status);
        p.setItens(new ArrayList<>());
        return p;
    }

    //criar

    @Nested
    class Criar {

        @Test
        void totalEhCalculadoComOPrecoDoBanco() {
            Usuario maria = usuario(1, EMAIL);
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(produtoRepository.findById(1)).thenReturn(Optional.of(produto(1, "Pastel", "12.50", true)));
            when(produtoRepository.findById(2)).thenReturn(Optional.of(produto(2, "Caldo de Cana", "8.00", true)));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(c -> c.getArgument(0));

            Pedido pedido = pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(1, 2), new ItemRequest(2, 3)));

            // 2 x 12.50 + 3 x 8.00 = 49.00
            assertThat(pedido.getValorTotal()).isEqualByComparingTo("49.00");
            assertThat(pedido.getItens()).hasSize(2);
            assertThat(pedido.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("12.50");
            assertThat(pedido.getItens().get(0).getPedido()).isSameAs(pedido);
        }

        @Test
        void donoStatusEFormaDePagamentoSaoDecididosPeloServidor() {
            Usuario maria = usuario(1, EMAIL);
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(produtoRepository.findById(1)).thenReturn(Optional.of(produto(1, "Pastel", "12.50", true)));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(c -> c.getArgument(0));

            Pedido pedido = pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(1, 1)));

            assertThat(pedido.getUsuario()).isSameAs(maria);
            assertThat(pedido.getStatus()).isEqualTo("PENDENTE");
            assertThat(pedido.getStatusPagamento()).isEqualTo(StatusPagamento.AGUARDANDO);
            assertThat(pedido.getMetodoPagamento()).isEqualTo("MERCADO_PAGO");
        }

        @Test
        void enderecoEhLimpoAntesDeSalvar() {
            Usuario maria = usuario(1, EMAIL);
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(produtoRepository.findById(1)).thenReturn(Optional.of(produto(1, "Pastel", "12.50", true)));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(c -> c.getArgument(0));

            Pedido pedido = pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(1, 1)));

            assertThat(pedido.getEndereco().getEstado()).isEqualTo("SC");
            assertThat(pedido.getEndereco().getRua()).isEqualTo("Rua das Flores");
            assertThat(pedido.getEndereco().getComplemento()).isNull(); // "  " vira null
            assertThat(pedido.getEndereco().getUsuario()).isSameAs(maria);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 99})
        void quantidadeNosLimitesEhAceita(int quantidade) {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));
            when(produtoRepository.findById(1)).thenReturn(Optional.of(produto(1, "Pastel", "1.00", true)));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(c -> c.getArgument(0));

            Pedido pedido = pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(1, quantidade)));

            assertThat(pedido.getValorTotal()).isEqualByComparingTo(String.valueOf(quantidade));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 100, Integer.MAX_VALUE})
        void quantidadeForaDosLimitesEhRecusada(int quantidade) {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(1, quantidade))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Erro: quantidade inválida. Use de 1 a 99.");

            verify(pedidoRepository, never()).save(any());
        }

        @Test
        void quantidadeNulaEhRecusada() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(1, null))))
                    .isInstanceOf(RegraDeNegocioException.class);
        }

        @Test
        void produtoQueNaoExisteEhRecusado() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));
            when(produtoRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(99, 1))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Erro: produto 99 não encontrado.");

            verify(pedidoRepository, never()).save(any());
        }

        @Test
        void produtoDesativadoEhRecusado() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));
            when(produtoRepository.findById(5)).thenReturn(Optional.of(produto(5, "Pamonha", "9.00", false)));

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(5, 1))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Erro: o produto \"Pamonha\" não está mais disponível. Remova-o do carrinho.");

            verify(pedidoRepository, never()).save(any());
        }

        @Test
        void umItemRuimNoMeioDerrubaOPedidoInteiro() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));
            when(produtoRepository.findById(1)).thenReturn(Optional.of(produto(1, "Pastel", "12.50", true)));

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(1, 1), new ItemRequest(1, 500))))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(pedidoRepository, never()).save(any());
        }

        @Test
        void itemSemProdutoEhRecusado() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, pedidoCom(new ItemRequest(null, 1))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Erro: item do pedido sem produto.");
            assertThatThrownBy(() -> pedidoService.criar(EMAIL, pedidoCom((ItemRequest) null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Erro: item do pedido sem produto.");
        }

        @Test
        void pedidoSemItensEhRecusado() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, new PedidoRequest(enderecoValido(), List.of())))
                    .hasMessage("Erro: o pedido não tem itens.");
            assertThatThrownBy(() -> pedidoService.criar(EMAIL, new PedidoRequest(enderecoValido(), null)))
                    .hasMessage("Erro: o pedido não tem itens.");
        }

        @Test
        void enderecoInvalidoEhRecusadoAntesDeOlharOsProdutos() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));
            EnderecoRequest semCep = new EnderecoRequest
                    ("", "Rua", "1", null, "Centro", "Floripa", "SC");

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, new PedidoRequest(semCep, List.of(new ItemRequest(1, 1)))))
                    .hasMessage("Erro: informe um CEP válido.");

            verifyNoInteractions(produtoRepository);
        }

        @Test
        void semDadosEhRecusado() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, null))
                    .hasMessage("Erro: dados do pedido não enviados.");
        }

        @Test
        void usuarioDoTokenQueNaoExisteMaisNaoCriaPedido() {
            when(usuarioService.buscarLogado(EMAIL)).thenThrow(new NaoAutenticadoException
                    ("Erro: usuário do token não existe mais."));

            assertThatThrownBy(() -> pedidoService.criar(EMAIL, pedidoCom(new ItemRequest
                    (1, 1))))
                    .isInstanceOf(NaoAutenticadoException.class);

            verifyNoInteractions(pedidoRepository, produtoRepository);
        }
    }

    //pagamento

    @Nested
    class Pagamento {

        @Test
        void devolveOLinkDoMercadoPago() {
            Pedido pedido = pedidoDe(usuario(1, EMAIL), 10, StatusPagamento.AGUARDANDO);
            when(mercadoPago.criarPreferencia(pedido, null))
                    .thenReturn(new PreferenciaResposta("pref-1", "https://mp.exemplo/pagar", null));

            PagamentoIniciado resultado = pedidoService.iniciarPagamento(pedido);

            assertThat(resultado).isEqualTo(PagamentoIniciado.comLink(10, "https://mp.exemplo/pagar"));
        }

        @Test
        void mercadoPagoForaDoArDevolveErroMasOPedidoContinua() {
            Pedido pedido = pedidoDe(usuario(1, EMAIL), 10, StatusPagamento.AGUARDANDO);
            when(mercadoPago.criarPreferencia(pedido, null)).thenThrow
                    (new PagamentoException("Mercado Pago indisponível.", null));

            PagamentoIniciado resultado = pedidoService.iniciarPagamento(pedido);

            assertThat(resultado.pedidoId()).isEqualTo(10);
            assertThat(resultado.linkPagamento()).isNull();
            assertThat(resultado.erro()).startsWith("Não foi possível abrir o pagamento agora.");
        }

        @Test
        void pagarDeNovoSoComPedidoAguardando() {
            Usuario maria = usuario(1, EMAIL);
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(pedidoRepository.buscarComItens(10)).thenReturn(Optional.of(pedidoDe(maria,
                    10, StatusPagamento.PAGO)));

            assertThatThrownBy(() -> pedidoService.pagarNovamente(EMAIL, 10))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Erro: este pedido não está aguardando pagamento.");

            verifyNoInteractions(mercadoPago);
        }

        @Test
        void pagarDeNovoPedidoAguardandoGeraNovoLink() {
            Usuario maria = usuario(1, EMAIL);
            Pedido pedido = pedidoDe(maria, 10, StatusPagamento.AGUARDANDO);
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(pedidoRepository.buscarComItens(10)).thenReturn(Optional.of(pedido));
            when(mercadoPago.criarPreferencia(pedido, null)).thenReturn
                    (new PreferenciaResposta("p", "https://link", null));

            assertThat(pedidoService.pagarNovamente(EMAIL, 10).linkPagamento()).isEqualTo("https://link");
        }
    }

    //pedido de outra pessoa (IDOR)

    @Nested
    class PedidoDeOutraPessoa {

        @Test
        void situacaoDePedidoAlheioDa404() {
            Usuario maria = usuario(1, EMAIL);
            Usuario joao = usuario(2, "joao@email.com");
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(pedidoRepository.findById(50)).thenReturn(Optional.of(pedidoDe(joao, 50, StatusPagamento.PAGO)));

            assertThatThrownBy(() -> pedidoService.situacao(EMAIL, 50))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Erro: pedido não encontrado.");
        }

        @Test
        void pedidoInexistenteDaAMesmaRespostaQuePedidoAlheio() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));
            when(pedidoRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pedidoService.situacao(EMAIL, 404))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Erro: pedido não encontrado.");
        }

        @Test
        void pagarPedidoAlheioDa404ENaoChamaOMercadoPago() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));
            when(pedidoRepository.buscarComItens(50))
                    .thenReturn(Optional.of(pedidoDe(usuario(2, "joao@email.com"), 50, StatusPagamento.AGUARDANDO)));

            assertThatThrownBy(() -> pedidoService.pagarNovamente(EMAIL, 50)).isInstanceOf(NaoEncontradoException.class);

            verifyNoInteractions(mercadoPago);
        }

        @Test
        void conferirPagamentoDePedidoAlheioDa404() {
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(usuario(1, EMAIL));
            when(pedidoRepository.findById(50))
                    .thenReturn(Optional.of(pedidoDe(usuario(2, "joao@email.com"), 50, StatusPagamento.AGUARDANDO)));

            assertThatThrownBy(() -> pedidoService.conferirPagamento(EMAIL, 50, "123"))
                    .isInstanceOf(NaoEncontradoException.class);

            verifyNoInteractions(confirmacaoPagamento);
        }
    }

    //conferir pagamento

    @Nested
    class ConferirPagamento {

        @Test
        void pedidoAguardandoConsultaOMercadoPagoEDevolveASituacaoAtualizada() {
            Usuario maria = usuario(1, EMAIL);
            Pedido antes = pedidoDe(maria, 10, StatusPagamento.AGUARDANDO);
            Pedido depois = pedidoDe(maria, 10, StatusPagamento.PAGO);
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(pedidoRepository.findById(10)).thenReturn(Optional.of(antes), Optional.of(depois));

            SituacaoPedido situacao = pedidoService.conferirPagamento(EMAIL, 10, "123");

            verify(confirmacaoPagamento).conferirPeloRetorno("123", 10);
            assertThat(situacao).isEqualTo(new SituacaoPedido(10, StatusPagamento.PAGO));
        }

        @Test
        void pedidoJaPagoComOutroPagamentoNaoConsultaDeNovo() {
            Usuario maria = usuario(1, EMAIL);
            Pedido pago = pedidoDe(maria, 10, StatusPagamento.PAGO);
            pago.setGatewayPagamentoId("999");
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(pedidoRepository.findById(10)).thenReturn(Optional.of(pago));

            pedidoService.conferirPagamento(EMAIL, 10, "123");

            verify(confirmacaoPagamento, never()).conferirPeloRetorno(anyString(), anyInt());
        }

        @Test
        void pedidoJaPagoComOMesmoPagamentoConsultaDeNovo() {

            Usuario maria = usuario(1, EMAIL);
            Pedido pago = pedidoDe(maria, 10, StatusPagamento.PAGO);
            pago.setGatewayPagamentoId("123");
            when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
            when(pedidoRepository.findById(10)).thenReturn(Optional.of(pago));

            pedidoService.conferirPagamento(EMAIL, 10, "123");

            verify(confirmacaoPagamento).conferirPeloRetorno("123", 10);
        }
    }

    //meus pedidos

    @Test
    void meusPedidosBuscaPeloIdDoUsuarioDoTokenEUsaOPrecoDaEpoca() {
        Usuario maria = usuario(1, EMAIL);
        Pedido pedido = pedidoDe(maria, 10, StatusPagamento.PAGO);
        ItemPedido item = new ItemPedido();
        item.setProduto(produto(1, "Pastel", "20.00", true));
        item.setQuantidade(2);
        item.setPrecoUnitario(new BigDecimal("12.50"));
        pedido.getItens().add(item);

        when(usuarioService.buscarLogado(EMAIL)).thenReturn(maria);
        when(pedidoRepository.buscarDoClienteComItens(1)).thenReturn(List.of(pedido));

        List<MeuPedido> meus = pedidoService.meusPedidos(EMAIL);

        assertThat(meus).hasSize(1);
        MeuPedido.Item linha = meus.get(0).itens().get(0);
        assertThat(linha.precoUnitario()).isEqualByComparingTo("12.50");
        assertThat(linha.subtotal()).isEqualByComparingTo("25.00");


        verify(pedidoRepository).buscarDoClienteComItens(1);
    }
}
