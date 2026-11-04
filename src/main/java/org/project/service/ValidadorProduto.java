package org.project.service;

import org.project.dto.ProdutoRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.regex.Pattern;

/*** Regras dos campos de produto (cadastro e edição no painel). Sem banco e sem Spring.
 * Limites iguais aos da tabela: nome VARCHAR(100), preco DECIMAL(10,2), caminho_imagem VARCHAR(255).*/

public final class ValidadorProduto {

    private static final int NOME_MAXIMO = 100;
    private static final int IMAGEM_MAXIMO = 255;
    private static final BigDecimal PRECO_MAXIMO = new BigDecimal("99999999.99");

    // Imagens que já estão na pasta imgs da loja: "../imgs/foto.jpeg" ou "/imgs/foto.jpeg".
    // Só letras, números, _ e -, sem ".." no nome (não deixa sair da pasta imgs).
    private static final Pattern IMAGEM_LOCAL = Pattern.compile(
            "^(\\.\\./|/)imgs/[\\p{L}\\p{N}_-]+(\\.[\\p{L}\\p{N}_-]+)*\\.(jpe?g|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    private ValidadorProduto() {
    }

    public static String validar(ProdutoRequest dados) {
        if (dados == null) {
            return "Erro: dados do produto não enviados.";
        }

        String nome = dados.nome() == null ? "" : dados.nome().trim();
        if (nome.isEmpty()) {
            return "Erro: o nome do produto é obrigatório.";
        }
        if (nome.length() > NOME_MAXIMO) {
            return "Erro: o nome pode ter no máximo " + NOME_MAXIMO + " caracteres.";
        }

        BigDecimal preco = dados.preco();
        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            return "Erro: o preço deve ser maior que zero.";
        }
        if (preco.stripTrailingZeros().scale() > 2) {
            return "Erro: o preço pode ter no máximo 2 casas decimais.";
        }
        if (preco.compareTo(PRECO_MAXIMO) > 0) {
            return "Erro: o preço máximo é 99999999.99.";
        }

        String imagem = dados.caminhoImagem() == null ? "" : dados.caminhoImagem().trim();
        if (imagem.isEmpty()) {
            return "Erro: a imagem do produto é obrigatória.";
        }
        if (imagem.length() > IMAGEM_MAXIMO) {
            return "Erro: o endereço da imagem pode ter no máximo " + IMAGEM_MAXIMO + " caracteres.";
        }
        if (!imagemValida(imagem)) {
            return "Erro: a imagem deve ser um endereço https:// ou um arquivo da pasta imgs (ex.: ../imgs/foto.jpeg).";
        }

        return null;
    }

    private static boolean imagemValida(String imagem) {
        if (IMAGEM_LOCAL.matcher(imagem).matches()) {
            return true;
        }
        try {
            URI uri = new URI(imagem);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}
