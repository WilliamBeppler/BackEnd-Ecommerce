package org.project.pagamento;

/**
 * Algo deu errado ao falar com o Mercado Pago (fora do ar, demorou demais, recusou o pedido).
 * A mensagem é para o LOG e para o desenvolvedor — nunca é mostrada ao cliente,
 * porque pode ter detalhes internos da integração.
 */
public class PagamentoException extends RuntimeException {

    public PagamentoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
