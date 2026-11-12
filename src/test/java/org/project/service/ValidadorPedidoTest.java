package org.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.project.dto.PedidoRequest.EnderecoRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras do endereço de entrega do checkout. */

class ValidadorPedidoTest {

    private static EnderecoRequest endereco(String cep, String rua, String numero, String complemento,
                                            String bairro, String cidade, String estado) {
        return new EnderecoRequest(cep, rua, numero, complemento, bairro, cidade, estado);
    }

    private static EnderecoRequest valido() {
        return endereco("88000-000", "Rua das Flores", "10", null, "Centro", "Florianópolis", "SC");
    }

    @Test
    void enderecoValido() {
        assertThat(ValidadorPedido.validarEndereco(valido())).isNull();
    }

    @Test
    void semEndereco() {
        assertThat(ValidadorPedido.validarEndereco(null)).isEqualTo("Erro: informe o endereço de entrega.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"88000-000", "88000000", " 88000-000 "})
    void cepsValidos(String cep) {
        assertThat(ValidadorPedido.validarEndereco(endereco(cep, "Rua", "1", null, "Centro", "Floripa", "SC"))).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"8800-000", "88000-0000", "88.000-000", "abcde-fgh"})
    void cepsInvalidos(String cep) {
        assertThat(ValidadorPedido.validarEndereco(endereco(cep, "Rua", "1", null, "Centro", "Floripa", "SC")))
                .isEqualTo("Erro: informe um CEP válido.");
    }

    @Test
    void ruaObrigatoriaEAte150() {
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", " ", "1", null, "C", "F", "SC")))
                .isEqualTo("Erro: informe a rua (até 150 caracteres).");
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "r".repeat(151), "1", null, "C", "F", "SC")))
                .isEqualTo("Erro: informe a rua (até 150 caracteres).");
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "r".repeat(150), "1", null, "C", "F", "SC")))
                .isNull();
    }

    @Test
    void numeroObrigatorioEAte20() {
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", null, null, "C", "F", "SC")))
                .isEqualTo("Erro: informe o número (até 20 caracteres).");
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1".repeat(21), null, "C", "F", "SC")))
                .isEqualTo("Erro: informe o número (até 20 caracteres).");
    }

    @Test
    void complementoEhOpcionalMasTemLimiteDe100() {
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", "", "C", "F", "SC"))).isNull();
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", "c".repeat(100), "C", "F", "SC"))).isNull();
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", "c".repeat(101), "C", "F", "SC")))
                .isEqualTo("Erro: o complemento pode ter no máximo 100 caracteres.");
    }

    @Test
    void bairroECidadeObrigatoriosEAte100() {
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", null, "", "F", "SC")))
                .isEqualTo("Erro: informe o bairro (até 100 caracteres).");
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", null, "b".repeat(101), "F", "SC")))
                .isEqualTo("Erro: informe o bairro (até 100 caracteres).");
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", null, "C", null, "SC")))
                .isEqualTo("Erro: informe a cidade (até 100 caracteres).");
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", null, "C", "c".repeat(101), "SC")))
                .isEqualTo("Erro: informe a cidade (até 100 caracteres).");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SC", "sc", " Sp "})
    void estadosAceitos(String uf) {
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", null, "C", "F", uf))).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"S", "SCC", "S1", "Santa Catarina", "<b>"})
    void estadosRecusados(String uf) {
        assertThat(ValidadorPedido.validarEndereco(endereco("88000-000", "Rua", "1", null, "C", "F", uf)))
                .isEqualTo("Erro: informe o estado com 2 letras (ex.: SC).");
    }
}
