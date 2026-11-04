package org.project.service.excecao;

/**
 * O que foi pedido não existe — ou existe, mas não pertence a quem pediu.
 * Nos dois casos a resposta é a mesma (HTTP 404), para não revelar que um
 * pedido de outra pessoa existe (proteção contra IDOR).
 */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
