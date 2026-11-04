package org.project.service;

import org.project.dto.CadastroRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Pattern;

/*** Regras dos campos do cadastro de cliente. Sem banco e sem Spring: testável direto.
 * Limites iguais aos da tabela usuarios (nome 100, email 150, telefone 50).*/

public final class ValidadorCadastro {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private ValidadorCadastro() {
    }

    public static String validar(CadastroRequest dados) {
        if (dados == null) {
            return "Erro: dados do cadastro não enviados.";
        }

        if (vazio(dados.nome()) || dados.nome().trim().length() > 100) {
            return "Erro: informe seu nome (até 100 caracteres).";
        }

        if (vazio(dados.email()) || dados.email().trim().length() > 150
                || !EMAIL.matcher(dados.email().trim()).matches()) {
            return "Erro: informe um e-mail válido.";
        }

        String senha = dados.senha() == null ? "" : dados.senha();
        if (senha.length() < 8) {
            return "Erro: a senha deve ter no mínimo 8 caracteres.";
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Erro: a senha é longa demais (máximo de 72 caracteres).";
        }

        if (!cpfValido(dados.cpf())) {
            return "Erro: informe um CPF válido.";
        }

        String digitosTelefone = dados.telefone() == null ? "" : dados.telefone().replaceAll("\\D", "");
        if (digitosTelefone.length() < 10 || digitosTelefone.length() > 11 || dados.telefone().trim().length() > 50) {
            return "Erro: informe um telefone válido com DDD.";
        }

        LocalDate nascimento = dados.nascimento();
        if (nascimento == null || nascimento.isAfter(LocalDate.now())
                || nascimento.isBefore(LocalDate.now().minusYears(120))) {
            return "Erro: informe uma data de nascimento válida.";
        }

        return null;
    }

    public static boolean cpfValido(String cpf) {
        if (cpf == null) return false;
        String d = cpf.replaceAll("\\D", "");
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false; // tamanho errado, ou 111.111.111-11 e parecidos
        }
        return d.charAt(9) == digitoVerificador(d, 9) && d.charAt(10) == digitoVerificador(d, 10);
    }

    public static String formatarCpf(String cpf) {
        String d = cpf.replaceAll("\\D", "");
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    private static char digitoVerificador(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return (char) ('0' + (resto == 10 ? 0 : resto));
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
