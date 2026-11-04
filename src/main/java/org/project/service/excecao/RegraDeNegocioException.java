package org.project.service.excecao;

/**
 * O pedido do cliente quebra uma regra do negócio ou chegou com dados inválidos
 * (ex.: CPF inválido, quantidade fora do limite, pedido que não aguarda pagamento).
 * O service só diz "não pode" e o motivo; quem transforma isso em HTTP 400 é o
 * TratadorDeErros, na camada de controller.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
