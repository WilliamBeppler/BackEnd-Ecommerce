package org.project.service;

import org.project.model.StatusPagamento;
import org.project.model.StatusProcessamento;

import java.util.Locale;
import java.util.regex.Pattern;

/*** Traduz o que o painel manda (aba e texto de busca) nos filtros da consulta.
 * Sem banco e sem Spring: testável chamando os métodos direto.*/

public final class FiltroPedidos {

    public enum Aba {
        TODOS(null, null),
        AGUARDANDO(StatusPagamento.AGUARDANDO, null),
        PAGO_NAO_PROCESSADO(StatusPagamento.PAGO, StatusProcessamento.NAO_PROCESSADO),
        PAGO_PROCESSADO(StatusPagamento.PAGO, StatusProcessamento.PROCESSADO),
        CANCELADOS(StatusPagamento.CANCELADO, null);

        public final StatusPagamento pagamento;
        public final StatusProcessamento processamento;

        Aba(StatusPagamento pagamento, StatusProcessamento processamento) {
            this.pagamento = pagamento;
            this.processamento = processamento;
        }
    }

    public record Busca(Integer id, String texto) {
    }

    private static final Pattern NUMERO_PEDIDO = Pattern.compile("^#?\\d{1,9}$");
    private static final int BUSCA_MAXIMA = 100;

    private FiltroPedidos() {
    }

    /** Aba vazia = TODOS. Aba desconhecida = null (o controller responde 400). */
    public static Aba interpretarAba(String aba) {
        if (aba == null || aba.isBlank()) return Aba.TODOS;
        try {
            return Aba.valueOf(aba.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Busca interpretarBusca(String busca) {
        if (busca == null || busca.isBlank()) return new Busca(null, null);

        String termo = busca.trim();
        if (termo.length() > BUSCA_MAXIMA) termo = termo.substring(0, BUSCA_MAXIMA);

        if (NUMERO_PEDIDO.matcher(termo).matches()) {
            return new Busca(Integer.parseInt(termo.replace("#", "")), null);
        }
        return new Busca(null, "%" + termo.toLowerCase(Locale.ROOT) + "%");
    }
}
