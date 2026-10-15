package org.project.dto;

import java.math.BigDecimal;

/**
 * O que o painel de admin pode mandar ao criar ou editar um produto.
 * Não tem id (vem sempre da URL) nem "ativo" (só muda pelas rotas de desativar/reativar):
 * o navegador não consegue escolher nenhum campo além destes três.
 */
public record ProdutoRequest(String nome, BigDecimal preco, String caminhoImagem) {
}
