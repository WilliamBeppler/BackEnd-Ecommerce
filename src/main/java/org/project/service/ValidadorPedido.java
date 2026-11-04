package org.project.service;

import org.project.dto.PedidoRequest.EnderecoRequest;

/*** Regras do endereço de entrega do checkout. Sem banco e sem Spring: testável direto.
 * Limites iguais aos da tabela enderecos.*/

public final class ValidadorPedido {

    private ValidadorPedido() {
    }

    public static String validarEndereco(EnderecoRequest e) {
        if (e == null) {
            return "Erro: informe o endereço de entrega.";
        }
        String digitosCep = e.cep() == null ? "" : e.cep().replaceAll("\\D", "");
        if (digitosCep.length() != 8 || e.cep().trim().length() > 9) {
            return "Erro: informe um CEP válido.";
        }
        if (vazio(e.rua()) || e.rua().trim().length() > 150) {
            return "Erro: informe a rua (até 150 caracteres).";
        }
        if (vazio(e.numero()) || e.numero().trim().length() > 20) {
            return "Erro: informe o número (até 20 caracteres).";
        }
        if (!vazio(e.complemento()) && e.complemento().trim().length() > 100) {
            return "Erro: o complemento pode ter no máximo 100 caracteres.";
        }
        if (vazio(e.bairro()) || e.bairro().trim().length() > 100) {
            return "Erro: informe o bairro (até 100 caracteres).";
        }
        if (vazio(e.cidade()) || e.cidade().trim().length() > 100) {
            return "Erro: informe a cidade (até 100 caracteres).";
        }
        if (e.estado() == null || !e.estado().trim().matches("[A-Za-z]{2}")) {
            return "Erro: informe o estado com 2 letras (ex.: SC).";
        }
        return null;
    }

    static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
