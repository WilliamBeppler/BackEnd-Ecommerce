package org.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.project.service.FiltroPedidos.Aba;
import org.project.service.FiltroPedidos.Busca;

import static org.assertj.core.api.Assertions.assertThat;

/** Como o texto das abas e da busca do painel vira filtro de consulta. */

class FiltroPedidosTest {

    //abas

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void abaVaziaEhTodos(String aba) {
        assertThat(FiltroPedidos.interpretarAba(aba)).isEqualTo(Aba.TODOS);
    }

    @Test
    void abaIgnoraMaiusculasEEspacos() {
        assertThat(FiltroPedidos.interpretarAba(" pago_processado ")).isEqualTo(Aba.PAGO_PROCESSADO);
        assertThat(FiltroPedidos.interpretarAba("Cancelados")).isEqualTo(Aba.CANCELADOS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"xyz", "PAGO", "TODOS; DROP TABLE pedidos"})
    void abaDesconhecidaViraNull(String aba) {
        assertThat(FiltroPedidos.interpretarAba(aba)).isNull();
    }

    @Test
    void cadaAbaApontaParaOsStatusCertos() {
        assertThat(Aba.TODOS.pagamento).isNull();
        assertThat(Aba.AGUARDANDO.processamento).isNull();
        assertThat(Aba.PAGO_NAO_PROCESSADO.processamento).isNotNull();
    }

    //busca

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void buscaVaziaNaoFiltra(String busca) {
        assertThat(FiltroPedidos.interpretarBusca(busca)).isEqualTo(new Busca(null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "#123", " #123 "})
    void numeroBuscaPeloIdDoPedido(String busca) {
        assertThat(FiltroPedidos.interpretarBusca(busca)).isEqualTo(new Busca(123, null));
    }

    @Test
    void numeroGrandeDemaisViraBuscaPorTexto() {

        assertThat(FiltroPedidos.interpretarBusca("1234567890")).isEqualTo(new Busca(null, "%1234567890%"));
    }

    @Test
    void textoViraLikeEmMinusculas() {
        assertThat(FiltroPedidos.interpretarBusca("  Maria@Email.COM ")).isEqualTo(new Busca(null, "%maria@email.com%"));
    }

    @Test
    void textoEhCortadoEm100Caracteres() {
        Busca busca = FiltroPedidos.interpretarBusca("a".repeat(300));
        assertThat(busca.texto()).hasSize(102); // 100 letras + os dois %
    }
}
