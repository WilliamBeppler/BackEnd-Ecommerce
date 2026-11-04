package org.project.service;

import org.project.model.StatusPagamento;
import org.project.model.StatusProcessamento;

import java.util.ArrayList;
import java.util.List;

import static org.project.model.StatusPagamento.*;
import static org.project.model.StatusProcessamento.*;

/*** As regras de mudança de status de um pedido, num lugar só.
 *** O mesmo método serve para VALIDAR uma mudança (o controller recusa com 400) e para
 * dizer ao painel QUAIS BOTÕES mostrar (acoesPermitidas). Assim o JavaScript nunca
 * tem uma cópia das regras que poderia discordar desta (lição da Parte 24 do guia).
 ** Não depende de banco nem de Spring: dá para testar chamando os métodos direto.*/

public final class RegrasPedido {

    public static final String MARCAR_PAGO = "MARCAR_PAGO";
    public static final String CANCELAR = "CANCELAR";
    public static final String PROCESSAR = "PROCESSAR";
    public static final String DESFAZER_PROCESSAMENTO = "DESFAZER_PROCESSAMENTO";

    private RegrasPedido() {
    }

    /** Devolve o motivo do bloqueio, ou null se a mudança de PAGAMENTO é permitida. */
    public static String motivoBloqueioPagamento(StatusPagamento atual,
                                                 StatusProcessamento processamento,
                                                 StatusPagamento novo) {
        if (novo == atual) {
            return "O pagamento já está como " + atual + ".";
        }
        if (atual == CANCELADO) {
            return "Pedido cancelado não pode mudar de situação.";
        }
        if (novo == AGUARDANDO) {
            return "Não é possível voltar um pedido para aguardando pagamento.";
        }
        if (novo == CANCELADO && processamento == PROCESSADO) {
            return "Este pedido já foi processado. Desfaça o processamento antes de cancelar.";
        }
        return null; // AGUARDANDO → PAGO, AGUARDANDO → CANCELADO, PAGO (não processado) → CANCELADO
    }

    /** Devolve o motivo do bloqueio, ou null se a mudança de PROCESSAMENTO é permitida. */
    public static String motivoBloqueioProcessamento(StatusPagamento pagamento,
                                                     StatusProcessamento atual,
                                                     StatusProcessamento novo) {
        if (novo == atual) {
            return "O processamento já está como " + atual + ".";
        }
        if (pagamento != PAGO) {
            return "Só pedidos pagos podem ser processados.";
        }
        return null; // pago: NAO_PROCESSADO ↔ PROCESSADO
    }

    /** Quais botões o painel deve mostrar para um pedido nesta situação. */
    public static List<String> acoesPermitidas(StatusPagamento pagamento, StatusProcessamento processamento) {
        List<String> acoes = new ArrayList<>();
        if (motivoBloqueioPagamento(pagamento, processamento, PAGO) == null) acoes.add(MARCAR_PAGO);
        if (motivoBloqueioProcessamento(pagamento, processamento, PROCESSADO) == null) acoes.add(PROCESSAR);
        if (motivoBloqueioProcessamento(pagamento, processamento, NAO_PROCESSADO) == null) acoes.add(DESFAZER_PROCESSAMENTO);
        if (motivoBloqueioPagamento(pagamento, processamento, CANCELADO) == null) acoes.add(CANCELAR);
        return acoes;
    }
}
