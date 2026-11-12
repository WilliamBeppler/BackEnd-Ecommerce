package org.project.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.project.dto.CadastroRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorCadastroTest {


    private static final String CPF_VALIDO = "529.982.247-25";

    private static CadastroRequest valido() {
        return new CadastroRequest("Maria Silva", "maria@email.com", "senhaForte1",
                CPF_VALIDO, "(48) 99999-9999", LocalDate.of(1990, 5, 20));
    }

    private static CadastroRequest comNome(String nome) {
        CadastroRequest v = valido();
        return new CadastroRequest(nome, v.email(), v.senha(), v.cpf(), v.telefone(), v.nascimento());
    }

    private static CadastroRequest comEmail(String email) {
        CadastroRequest v = valido();
        return new CadastroRequest(v.nome(), email, v.senha(), v.cpf(), v.telefone(), v.nascimento());
    }

    private static CadastroRequest comSenha(String senha) {
        CadastroRequest v = valido();
        return new CadastroRequest(v.nome(), v.email(), senha, v.cpf(), v.telefone(), v.nascimento());
    }

    private static CadastroRequest comCpf(String cpf) {
        CadastroRequest v = valido();
        return new CadastroRequest(v.nome(), v.email(), v.senha(), cpf, v.telefone(), v.nascimento());
    }

    private static CadastroRequest comTelefone(String telefone) {
        CadastroRequest v = valido();
        return new CadastroRequest(v.nome(), v.email(), v.senha(), v.cpf(), telefone, v.nascimento());
    }

    private static CadastroRequest comNascimento(LocalDate nascimento) {
        CadastroRequest v = valido();
        return new CadastroRequest(v.nome(), v.email(), v.senha(), v.cpf(), v.telefone(), nascimento);
    }

    @Test
    @DisplayName("cadastro com todos os campos certos passa (devolve null)")
    void cadastroValido() {
        assertThat(ValidadorCadastro.validar(valido())).isNull();
    }

    @Test
    void semDados() {
        assertThat(ValidadorCadastro.validar(null)).isEqualTo("Erro: dados do cadastro não enviados.");
    }

    //nome

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nomeVazio(String nome) {
        assertThat(ValidadorCadastro.validar(comNome(nome))).isEqualTo("Erro: informe seu nome (até 100 caracteres).");
    }

    @Test
    void nomeNoLimiteDe100Passa_e101Falha() {
        assertThat(ValidadorCadastro.validar(comNome("a".repeat(100)))).isNull();
        assertThat(ValidadorCadastro.validar(comNome("a".repeat(101)))).isNotNull();
    }

    //e-mail

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"semarroba.com", "a@b", "a b@c.com", "@email.com", "maria@"})
    void emailInvalido(String email) {
        assertThat(ValidadorCadastro.validar(comEmail(email))).isEqualTo("Erro: informe um e-mail válido.");
    }

    @Test
    void emailComEspacosNasPontasEhAceito() {
        assertThat(ValidadorCadastro.validar(comEmail("  maria@email.com  "))).isNull();
    }

    @Test
    void emailComMaisDe150CaracteresFalha() {
        String longo = "a".repeat(141) + "@email.com"; // 151 caracteres
        assertThat(ValidadorCadastro.validar(comEmail(longo))).isEqualTo("Erro: informe um e-mail válido.");
    }

    //senha

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567"})
    void senhaCurtaDemais(String senha) {
        assertThat(ValidadorCadastro.validar(comSenha(senha)))
                .isEqualTo("Erro: a senha deve ter no mínimo 8 caracteres.");
    }

    @Test
    void senhaDe8a72BytesPassa() {
        assertThat(ValidadorCadastro.validar(comSenha("a".repeat(8)))).isNull();
        assertThat(ValidadorCadastro.validar(comSenha("a".repeat(72)))).isNull();
    }

    @Test
    void senhaDe73BytesFalha() {
        assertThat(ValidadorCadastro.validar(comSenha("a".repeat(73))))
                .isEqualTo("Erro: a senha é longa demais (máximo de 72 caracteres).");
    }

    @Test
    @DisplayName("o limite é em BYTES: 37 letras 'ç' são 74 bytes e passam do limite do BCrypt")
    void limiteDaSenhaContaBytesNaoLetras() {
        String senha = "ç".repeat(37);
        assertThat(senha).hasSize(37);
        assertThat(ValidadorCadastro.validar(comSenha(senha)))
                .isEqualTo("Erro: a senha é longa demais (máximo de 72 caracteres).");
    }

    //CPF

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
    void cpfsValidos(String cpf) {
        assertThat(ValidadorCadastro.cpfValido(cpf)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "529.982.247-26",
            "529.982.247-15",
            "111.111.111-11",
            "000.000.000-00",
            "5299822472",
            "529982247250",
            "abc.def.ghi-jk"})
    void cpfsInvalidos(String cpf) {
        assertThat(ValidadorCadastro.cpfValido(cpf)).isFalse();
        assertThat(ValidadorCadastro.validar(comCpf(cpf))).isEqualTo("Erro: informe um CPF válido.");
    }

    @Test
    void formataCpfSoComDigitos() {
        assertThat(ValidadorCadastro.formatarCpf("52998224725")).isEqualTo("529.982.247-25");
        assertThat(ValidadorCadastro.formatarCpf("529.982.247-25")).isEqualTo("529.982.247-25");
    }

    //telefone

    @ParameterizedTest
    @ValueSource(strings = {"(48) 3333-4444", "48999999999", "48 99999 9999"})
    void telefonesValidos(String telefone) {
        assertThat(ValidadorCadastro.validar(comTelefone(telefone))).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"99999-9999", "489999999999", "telefone"})
    void telefonesInvalidos(String telefone) {
        assertThat(ValidadorCadastro.validar(comTelefone(telefone)))
                .isEqualTo("Erro: informe um telefone válido com DDD.");
    }

    //nascimento

    @Test
    void nascimentoNuloFuturoOuMuitoAntigoFalha() {
        String erro = "Erro: informe uma data de nascimento válida.";
        assertThat(ValidadorCadastro.validar(comNascimento(null))).isEqualTo(erro);
        assertThat(ValidadorCadastro.validar(comNascimento(LocalDate.now().plusDays(1)))).isEqualTo(erro);
        assertThat(ValidadorCadastro.validar(comNascimento(LocalDate.now().minusYears(121)))).isEqualTo(erro);
    }
}
