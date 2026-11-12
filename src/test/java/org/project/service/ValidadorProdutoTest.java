package org.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.project.dto.ProdutoRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorProdutoTest {

    private static final String ERRO_IMAGEM =
            "Erro: a imagem deve ser um endereço https:// ou um arquivo da pasta imgs (ex.: ../imgs/foto.jpeg).";

    private static ProdutoRequest produto(String nome, String preco, String imagem) {
        return new ProdutoRequest(nome, preco == null ? null : new BigDecimal(preco), imagem);
    }

    private static ProdutoRequest comImagem(String imagem) {
        return produto("Pastel de Carne", "12.50", imagem);
    }

    private static ProdutoRequest comPreco(String preco) {
        return produto("Pastel de Carne", preco, "../imgs/img_pastelCarne.jpeg");
    }

    @Test
    void produtoValido() {
        assertThat(ValidadorProduto.validar(comPreco("12.50"))).isNull();
    }

    @Test
    void semDados() {
        assertThat(ValidadorProduto.validar(null)).isEqualTo("Erro: dados do produto não enviados.");
    }

    //nome

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nomeObrigatorio(String nome) {
        assertThat(ValidadorProduto.validar(produto(nome, "10", "../imgs/a.jpeg")))
                .isEqualTo("Erro: o nome do produto é obrigatório.");
    }

    @Test
    void nomeAte100Caracteres() {
        assertThat(ValidadorProduto.validar(produto("a".repeat(100), "10", "../imgs/a.jpeg"))).isNull();
        assertThat(ValidadorProduto.validar(produto("a".repeat(101), "10", "../imgs/a.jpeg")))
                .isEqualTo("Erro: o nome pode ter no máximo 100 caracteres.");
    }

    //preço

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void precoZeroOuNegativoFalha(String preco) {
        assertThat(ValidadorProduto.validar(comPreco(preco))).isEqualTo("Erro: o preço deve ser maior que zero.");
    }

    @Test
    void precoNuloFalha() {
        assertThat(ValidadorProduto.validar(comPreco(null))).isEqualTo("Erro: o preço deve ser maior que zero.");
    }

    @Test
    void precoComTresCasasDecimaisFalha() {
        assertThat(ValidadorProduto.validar(comPreco("12.505")))
                .isEqualTo("Erro: o preço pode ter no máximo 2 casas decimais.");
    }

    @Test
    void zerosAMaisNoFimNaoContamComoCasaDecimal() {
        assertThat(ValidadorProduto.validar(comPreco("12.500"))).isNull();
    }

    @Test
    void precoMaximoDaColunaDecimal10_2() {
        assertThat(ValidadorProduto.validar(comPreco("99999999.99"))).isNull();
        assertThat(ValidadorProduto.validar(comPreco("100000000.00"))).isEqualTo("Erro: o preço máximo é 99999999.99.");
    }

    @Test
    void menorPrecoPossivelEhUmCentavo() {
        assertThat(ValidadorProduto.validar(comPreco("0.01"))).isNull();
    }

    //imagem: casos bons

    @ParameterizedTest
    @ValueSource(strings = {
            "../imgs/img_pastelCarne.jpeg",
            "/imgs/foto.png",
            "../imgs/foto-nova.webp",
            "../imgs/FOTO.JPG",
            "../imgs/espaço_loja2.jpeg",
            "https://cdn.exemplo.com/pastel.jpg"})
    void imagensAceitas(String imagem) {
        assertThat(ValidadorProduto.validar(comImagem(imagem))).isNull();
    }

    //imagem: ataques e erros

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "data:image/png;base64,iVBORw0KGgo=",
            "http://exemplo.com/foto.jpg",
            "https://",
            "//exemplo.com/foto.jpg",
            "../imgs/../segredo.jpeg",
            "../imgs/../../etc/passwd.png",
            "../imgs/foto.svg",
            "../imgs/foto.jpeg?x=1",
            "../outra/foto.jpeg",
            "imgs/foto.jpeg",
            "https://exemplo.com/foto com espaço.jpg"})
    void imagensRecusadas(String imagem) {
        assertThat(ValidadorProduto.validar(comImagem(imagem))).isEqualTo(ERRO_IMAGEM);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void imagemObrigatoria(String imagem) {
        assertThat(ValidadorProduto.validar(comImagem(imagem))).isEqualTo("Erro: a imagem do produto é obrigatória.");
    }

    @Test
    void imagemComMaisDe255CaracteresFalha() {
        String longa = "https://exemplo.com/" + "a".repeat(240) + ".jpg"; // 264 caracteres
        assertThat(ValidadorProduto.validar(comImagem(longa)))
                .isEqualTo("Erro: o endereço da imagem pode ter no máximo 255 caracteres.");
    }
}
