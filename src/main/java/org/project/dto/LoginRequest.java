package org.project.dto;

/** O login só precisa disto; nenhum outro campo é aceito. */
public record LoginRequest(String email, String senha) {

    // Sem isto, o toString() automático do record imprimiria a senha em qualquer log
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", senha=***]";
    }
}
