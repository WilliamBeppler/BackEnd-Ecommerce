package org.project.service.excecao;

/**
 * Não dá para saber quem é a pessoa: o token é válido, mas o usuário não existe
 * mais, ou o e-mail e a senha não conferem. Vira HTTP 401.
 */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
