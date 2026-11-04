package org.project.model;

/**
 * Situação do ENVIO do pedido (separar, embalar, despachar).
 * Sempre muda pela mão do admin: o gateway sabe de dinheiro, não de embalagem.
 */
public enum StatusProcessamento {
    NAO_PROCESSADO,
    PROCESSADO
}
