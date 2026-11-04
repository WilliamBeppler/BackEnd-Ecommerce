package org.project.model;

/**
 * Situação do DINHEIRO do pedido. Hoje quem muda é o admin (botão temporário);
 * quando houver gateway, é o webhook dele que vai marcar PAGO ou CANCELADO.
 * Enum em vez de texto: um status escrito errado vira erro de compilação, não lixo no banco.
 */
public enum StatusPagamento {
    AGUARDANDO,
    PAGO,
    CANCELADO
}
