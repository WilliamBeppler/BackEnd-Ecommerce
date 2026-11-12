package org.project.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.project.model.StatusPagamento.AGUARDANDO;
import static org.project.model.StatusPagamento.CANCELADO;
import static org.project.model.StatusPagamento.PAGO;
import static org.project.model.StatusProcessamento.NAO_PROCESSADO;
import static org.project.model.StatusProcessamento.PROCESSADO;


class RegrasPedidoTest {

    @Nested
    class Pagamento {

        @Test
        void aguardandoPodeVirarPagoOuCancelado() {
            assertThat(RegrasPedido.motivoBloqueioPagamento(AGUARDANDO, NAO_PROCESSADO, PAGO)).isNull();
            assertThat(RegrasPedido.motivoBloqueioPagamento(AGUARDANDO, NAO_PROCESSADO, CANCELADO)).isNull();
        }

        @Test
        void pagoNaoProcessadoPodeSerCancelado() {
            assertThat(RegrasPedido.motivoBloqueioPagamento(PAGO, NAO_PROCESSADO, CANCELADO)).isNull();
        }

        @Test
        void pagoJaProcessadoNaoPodeSerCancelado() {
            assertThat(RegrasPedido.motivoBloqueioPagamento(PAGO, PROCESSADO, CANCELADO))
                    .isEqualTo("Este pedido já foi processado. Desfaça o processamento antes de cancelar.");
        }

        @Test
        void naoVoltaParaAguardando() {
            assertThat(RegrasPedido.motivoBloqueioPagamento(PAGO, NAO_PROCESSADO, AGUARDANDO))
                    .isEqualTo("Não é possível voltar um pedido para aguardando pagamento.");
        }

        @Test
        void canceladoEhDefinitivo() {
            assertThat(RegrasPedido.motivoBloqueioPagamento(CANCELADO, NAO_PROCESSADO, PAGO))
                    .isEqualTo("Pedido cancelado não pode mudar de situação.");
        }

        @Test
        void mudarParaOMesmoStatusEhBloqueado() {
            assertThat(RegrasPedido.motivoBloqueioPagamento(PAGO, NAO_PROCESSADO, PAGO))
                    .isEqualTo("O pagamento já está como PAGO.");
        }
    }

    @Nested
    class Processamento {

        @Test
        void soPedidoPagoPodeSerProcessado() {
            assertThat(RegrasPedido.motivoBloqueioProcessamento(AGUARDANDO, NAO_PROCESSADO, PROCESSADO))
                    .isEqualTo("Só pedidos pagos podem ser processados.");
            assertThat(RegrasPedido.motivoBloqueioProcessamento(CANCELADO, NAO_PROCESSADO, PROCESSADO))
                    .isEqualTo("Só pedidos pagos podem ser processados.");
        }

        @Test
        void pagoVaiEVoltaEntreNaoProcessadoEProcessado() {
            assertThat(RegrasPedido.motivoBloqueioProcessamento(PAGO, NAO_PROCESSADO, PROCESSADO)).isNull();
            assertThat(RegrasPedido.motivoBloqueioProcessamento(PAGO, PROCESSADO, NAO_PROCESSADO)).isNull();
        }

        @Test
        void mudarParaOMesmoStatusEhBloqueado() {
            assertThat(RegrasPedido.motivoBloqueioProcessamento(PAGO, PROCESSADO, PROCESSADO))
                    .isEqualTo("O processamento já está como PROCESSADO.");
        }
    }

    @Nested
    class BotoesDoPainel {

        @Test
        void aguardando() {
            assertThat(RegrasPedido.acoesPermitidas(AGUARDANDO, NAO_PROCESSADO))
                    .containsExactly(RegrasPedido.MARCAR_PAGO, RegrasPedido.CANCELAR);
        }

        @Test
        void pagoNaoProcessado() {
            assertThat(RegrasPedido.acoesPermitidas(PAGO, NAO_PROCESSADO))
                    .containsExactly(RegrasPedido.PROCESSAR, RegrasPedido.CANCELAR);
        }

        @Test
        void pagoProcessado() {
            assertThat(RegrasPedido.acoesPermitidas(PAGO, PROCESSADO))
                    .containsExactly(RegrasPedido.DESFAZER_PROCESSAMENTO);
        }

        @Test
        void canceladoNaoTemNenhumBotao() {
            assertThat(RegrasPedido.acoesPermitidas(CANCELADO, NAO_PROCESSADO)).isEmpty();
        }
    }
}
