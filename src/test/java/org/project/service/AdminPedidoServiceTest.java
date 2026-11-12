package org.project.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.dto.admin.MudarStatusRequest;
import org.project.dto.admin.PaginaPedidos;
import org.project.dto.admin.PedidoDetalhe;
import org.project.model.Pedido;
import org.project.model.PedidoHistorico;
import org.project.model.StatusPagamento;
import org.project.model.StatusProcessamento;
import org.project.model.Usuario;
import org.project.repository.PedidoHistoricoRepository;
import org.project.repository.PedidoRepository;
import org.project.service.excecao.NaoEncontradoException;
import org.project.service.excecao.RegraDeNegocioException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pedidos no painel de admin: lista com abas, e mudança de status com histórico. */

@ExtendWith(MockitoExtension.class)
class AdminPedidoServiceTest {

    private static final String ADMIN = "admin@loja.com";

    @Mock PedidoRepository pedidoRepository;
    @Mock PedidoHistoricoRepository historicoRepository;
    @InjectMocks AdminPedidoService service;

    private static Pedido pedido(int id, StatusPagamento pagamento, StatusProcessamento processamento) {
        Usuario cliente = new Usuario();
        cliente.setNome("Maria");
        cliente.setEmail("maria@email.com");
        Pedido p = new Pedido();
        p.setId(id);
        p.setUsuario(cliente);
        p.setStatusPagamento(pagamento);
        p.setStatusProcessamento(processamento);
        return p;
    }

    private void prepararSalvar(Pedido p) {
        when(pedidoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(pedidoRepository.save(p)).thenReturn(p);
        when(historicoRepository.findByPedidoIdOrderByDataDescIdDesc(p.getId())).thenReturn(List.of());
    }

    //lista

    @Nested
    class Lista {

        private void prepararContadores() {
            when(pedidoRepository.buscarParaPainel(any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(pedidoRepository.count()).thenReturn(10L);
            when(pedidoRepository.countByStatusPagamento(StatusPagamento.AGUARDANDO)).thenReturn(3L);
            when(pedidoRepository.countByStatusPagamento(StatusPagamento.CANCELADO)).thenReturn(1L);
            when(pedidoRepository.countByStatusPagamentoAndStatusProcessamento(StatusPagamento.PAGO, StatusProcessamento.NAO_PROCESSADO)).thenReturn(4L);
            when(pedidoRepository.countByStatusPagamentoAndStatusProcessamento(StatusPagamento.PAGO, StatusProcessamento.PROCESSADO)).thenReturn(2L);
        }

        @Test
        void abaInvalidaDa400SemConsultarOBanco() {
            assertThatThrownBy(() -> service.listar("inventada", null, 0, 20))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Erro: aba inválida.");
            verifyNoInteractions(pedidoRepository);
        }

        @Test
        void contadorDeCadaAba() {
            prepararContadores();

            PaginaPedidos pagina = service.listar(null, null, 0, 20);

            assertThat(pagina.contadores()).containsExactly(
                    java.util.Map.entry("TODOS", 10L),
                    java.util.Map.entry("AGUARDANDO", 3L),
                    java.util.Map.entry("PAGO_NAO_PROCESSADO", 4L),
                    java.util.Map.entry("PAGO_PROCESSADO", 2L),
                    java.util.Map.entry("CANCELADOS", 1L));
        }

        @Test
        void paginaETamanhoForaDaFaixaSaoCorrigidos() {
            prepararContadores();

            PaginaPedidos pagina = service.listar(null, null, -5, 1000);

            ArgumentCaptor<Pageable> pedidoDePagina = ArgumentCaptor.forClass(Pageable.class);
            verify(pedidoRepository).buscarParaPainel(any(), any(), any(), any(), pedidoDePagina.capture());
            assertThat(pedidoDePagina.getValue().getPageNumber()).isZero();
            assertThat(pedidoDePagina.getValue().getPageSize()).isEqualTo(50); // nunca mais que 50 por página
            assertThat(pagina.tamanho()).isEqualTo(50);
        }

        @Test
        void tamanhoZeroViraUm() {
            prepararContadores();
            assertThat(service.listar(null, null, 0, 0).tamanho()).isEqualTo(1);
        }

        @Test
        void maisRecentesPrimeiro() {
            prepararContadores();

            service.listar(null, null, 0, 20);

            ArgumentCaptor<Pageable> pedidoDePagina = ArgumentCaptor.forClass(Pageable.class);
            verify(pedidoRepository).buscarParaPainel(any(), any(), any(), any(), pedidoDePagina.capture());
            Sort ordem = pedidoDePagina.getValue().getSort();
            assertThat(ordem.getOrderFor("dataPedido").getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(ordem.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void abaEBuscaViramOsFiltrosDaConsulta() {
            prepararContadores();

            service.listar("pago_processado", "#7", 0, 20);
            verify(pedidoRepository).buscarParaPainel(eq(StatusPagamento.PAGO), eq(StatusProcessamento.PROCESSADO),
                    eq(7), isNull(), any(Pageable.class));

            service.listar("aguardando", "Maria", 0, 20);
            verify(pedidoRepository).buscarParaPainel(eq(StatusPagamento.AGUARDANDO), isNull(),
                    isNull(), eq("%maria%"), any(Pageable.class));
        }
    }

    //pagamento

    @Nested
    class MudarPagamento {

        @Test
        void marcarComoPagoGravaOHistoricoComQuemMudou() {
            Pedido p = pedido(1, StatusPagamento.AGUARDANDO, StatusProcessamento.NAO_PROCESSADO);
            prepararSalvar(p);

            PedidoDetalhe detalhe = service.mudarPagamento(1, new MudarStatusRequest(" pago ", null), ADMIN);

            assertThat(detalhe.statusPagamento()).isEqualTo(StatusPagamento.PAGO);
            ArgumentCaptor<PedidoHistorico> historico = ArgumentCaptor.forClass(PedidoHistorico.class);
            verify(historicoRepository).save(historico.capture());
            assertThat(historico.getValue().getCampo()).isEqualTo("PAGAMENTO");
            assertThat(historico.getValue().getDe()).isEqualTo("AGUARDANDO");
            assertThat(historico.getValue().getPara()).isEqualTo("PAGO");
            assertThat(historico.getValue().getAdminEmail()).isEqualTo(ADMIN);
        }

        @Test
        void statusDesconhecidoOuAusenteDa400() {
            when(pedidoRepository.findById(1))
                    .thenReturn(Optional.of(pedido(1, StatusPagamento.AGUARDANDO, StatusProcessamento.NAO_PROCESSADO)));

            assertThatThrownBy(() -> service.mudarPagamento(1, new MudarStatusRequest("ESTORNADO", null), ADMIN))
                    .hasMessage("Erro: status de pagamento inválido.");
            assertThatThrownBy(() -> service.mudarPagamento(1, new MudarStatusRequest(null, null), ADMIN))
                    .hasMessage("Erro: status de pagamento inválido.");
            assertThatThrownBy(() -> service.mudarPagamento(1, null, ADMIN))
                    .hasMessage("Erro: status de pagamento inválido.");

            verify(pedidoRepository, never()).save(any());
        }

        @Test
        void transicaoProibidaDa400ENaoGravaNada() {
            when(pedidoRepository.findById(1))
                    .thenReturn(Optional.of(pedido(1, StatusPagamento.CANCELADO, StatusProcessamento.NAO_PROCESSADO)));

            assertThatThrownBy(() -> service.mudarPagamento(1, new MudarStatusRequest("PAGO", null), ADMIN))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Erro: Pedido cancelado não pode mudar de situação.");

            verify(pedidoRepository, never()).save(any());
            verify(historicoRepository, never()).save(any());
        }

        @Test
        void pedidoInexistenteDa404() {
            when(pedidoRepository.findById(anyInt())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.mudarPagamento(77, new MudarStatusRequest("PAGO", null), ADMIN))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Erro: pedido 77 não encontrado.");
        }
    }

    //processamento

    @Nested
    class MudarProcessamento {

        @Test
        void processarGuardaOCodigoDeRastreioSemEspacos() {
            Pedido p = pedido(1, StatusPagamento.PAGO, StatusProcessamento.NAO_PROCESSADO);
            prepararSalvar(p);

            PedidoDetalhe detalhe = service.mudarProcessamento(1, new MudarStatusRequest("PROCESSADO", "  BR123456789  "), ADMIN);

            assertThat(detalhe.codigoRastreio()).isEqualTo("BR123456789");
            assertThat(detalhe.statusProcessamento()).isEqualTo(StatusProcessamento.PROCESSADO);
        }

        @Test
        void rastreioEmBrancoViraNull() {
            Pedido p = pedido(1, StatusPagamento.PAGO, StatusProcessamento.NAO_PROCESSADO);
            prepararSalvar(p);

            assertThat(service.mudarProcessamento(1, new MudarStatusRequest("PROCESSADO", "   "), ADMIN).codigoRastreio()).isNull();
        }

        @Test
        void rastreioComMaisDe50CaracteresDa400() {
            when(pedidoRepository.findById(1))
                    .thenReturn(Optional.of(pedido(1, StatusPagamento.PAGO, StatusProcessamento.NAO_PROCESSADO)));

            assertThatThrownBy(() -> service.mudarProcessamento(1, new MudarStatusRequest("PROCESSADO", "X".repeat(51)), ADMIN))
                    .hasMessage("Erro: o código de rastreio pode ter no máximo 50 caracteres.");

            verify(pedidoRepository, never()).save(any());
        }

        @Test
        void desfazerOProcessamentoApagaORastreio() {
            Pedido p = pedido(1, StatusPagamento.PAGO, StatusProcessamento.PROCESSADO);
            p.setCodigoRastreio("BR123");
            prepararSalvar(p);

            PedidoDetalhe detalhe = service.mudarProcessamento(1, new MudarStatusRequest("NAO_PROCESSADO", null), ADMIN);

            assertThat(detalhe.codigoRastreio()).isNull();
        }

        @Test
        void pedidoNaoPagoNaoPodeSerProcessado() {
            when(pedidoRepository.findById(1))
                    .thenReturn(Optional.of(pedido(1, StatusPagamento.AGUARDANDO, StatusProcessamento.NAO_PROCESSADO)));

            assertThatThrownBy(() -> service.mudarProcessamento(1, new MudarStatusRequest("PROCESSADO", null), ADMIN))
                    .hasMessage("Erro: Só pedidos pagos podem ser processados.");

            verify(historicoRepository, never()).save(any());
        }
    }
}
