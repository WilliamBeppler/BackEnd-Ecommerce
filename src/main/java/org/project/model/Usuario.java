package org.project.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity //Avisa que a classe é uma tabela
@Table(name = "usuarios") //Garante que o Spring conecte na tabela exata do MySQL
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate nascimento;

    @Column(nullable = false, length = 50)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String telefone;

    @Column(nullable = false, unique = true, length = 14)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String cpf;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // CRÍTICO: WRITE_ONLY, não @JsonIgnore.
    // WRITE_ONLY deixa o Jackson LER a senha do corpo da requisição (cadastro e
    // login precisam disso) mas nunca ESCREVE ela em nenhuma resposta JSON.
    // @JsonIgnore puro bloquearia os dois lados e quebraria o login.
    @Column(nullable = false, length = 255)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String senha;

    @Column(nullable = false)
    private String role = "CLIENTE";

    // A data será preenchida automaticamente pelo banco de dados
    @CreationTimestamp
    @Column(name = "data_registro", insertable = false, updatable = false)
    private LocalDateTime dataRegistro;

    public Usuario(){

    }

    public Usuario(String nome, LocalDate nascimento, String telefone, String cpf, String email, String senha) {
        this.nome = nome;
        this.nascimento = nascimento;
        this.telefone = telefone;
        this.cpf = cpf;
        this.email = email;
        this.senha = senha;
    }

    //---Getters e Setters---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public LocalDate getNascimento() {return nascimento;}

    public void setNascimento(LocalDate nascimento) {this.nascimento = nascimento;}

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public LocalDateTime getDataRegistro() { return dataRegistro; }
    public void setDataRegistro(LocalDateTime dataRegistro) { this.dataRegistro = dataRegistro; }

    public String getRole() {return role;}

    public void setRole(String role) {this.role = role;}

}