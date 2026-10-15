package org.project.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // READ_ONLY: o id aparece nas respostas, mas é IGNORADO se vier num JSON de entrada.
    // Segunda camada além dos DTOs: um "id" vindo do navegador nunca vira UPDATE de outro registro.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private int id;

    private String nome;

    // Dinheiro sempre em BigDecimal (Java) + DECIMAL(10,2) (banco), igual a Pedido e ItemPedido
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    private String caminho_imagem;

    // "Apagar" no painel só desativa: o produto some da vitrine, mas os pedidos
    // antigos continuam apontando para ele (apagar de verdade quebraria o histórico).
    // A coluna é criada à mão com DEFAULT TRUE (o Hibernate criaria os antigos como false).
    @Column(nullable = false)
    private boolean ativo = true;

    public Produto(){

    }

    public Produto(int id, String nome, BigDecimal preco, String caminho_imagem) {
        this.id = id;
        this.nome = nome;
        this.preco = preco;
        this.caminho_imagem = caminho_imagem;
    }

    // --- Getters e Setters ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public String getCaminho_imagem() {
        return caminho_imagem;
    }

    public void setCaminho_imagem(String caminho_imagem) {
        this.caminho_imagem = caminho_imagem;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

}
