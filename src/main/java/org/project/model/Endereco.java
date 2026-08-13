package org.project.model;

import jakarta.persistence.*;

@Entity
@Table(name = "enderecos")
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, length = 9)
    private String cep;

    @Column(nullable = false, length = 150)
    private String rua;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(length = 100) // Complemento não é obrigatório
    private String complemento;

    @Column(nullable = false,length = 100)
    private String bairro;

    @Column(nullable = false,length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String estado;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    public Endereco() {
    }

    // --- Getters e Setters ---
    public int getId() { return id; }
    public void stId(int id) {this.id = id;}

    public String getCep() {return cep;}
    public void setCep(String cep) {this.cep = cep;}

    public String getRua() {return rua;}
    public void setRua(String rua) {this.rua = rua;}

    public String getNumero() {return numero;}
    public void setNumero(String numero) {this.numero = numero;}

    public String getComplemento() {return complemento;}
    public void setComplemento(String complemento){this.complemento = complemento;}

    public String getBairro() {return bairro;}
    public void setBairro(String bairro) {this.bairro = bairro;}

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

}
