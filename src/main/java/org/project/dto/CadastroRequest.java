package org.project.dto;

import java.time.LocalDate;

/**
 * O que a tela de cadastro pode mandar.
 * Sem id (impede sobrescrever a conta de outra pessoa), sem role (ninguém se cadastra
 * como ADMIN) e sem dataRegistro: o navegador só escolhe estes seis campos.
 */
public record CadastroRequest(String nome,
                              String email,
                              String senha,
                              String cpf,
                              String telefone,
                              LocalDate nascimento) {

    @Override
    public String toString() {
        return "CadastroRequest[nome=" + nome + ", email=" + email + ", senha=***, cpf=***, telefone=***]";
    }
}
