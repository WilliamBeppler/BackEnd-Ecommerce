package org.project.model;

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
    private String telefone;

    @Column(nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String senha;

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

}
