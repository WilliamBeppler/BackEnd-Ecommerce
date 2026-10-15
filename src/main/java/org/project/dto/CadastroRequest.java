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

    // O toString() automático do record imprimiria a senha; se alguém logar este
    // objeto, ela iria parar no log em texto puro. CPF e telefone também ficam de fora.
    @Override
    public String toString() {
        return "CadastroRequest[nome=" + nome + ", email=" + email + ", senha=***, cpf=***, telefone=***]";
    }
}
