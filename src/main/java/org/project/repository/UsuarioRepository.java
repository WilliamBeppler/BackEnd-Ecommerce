package org.project.repository;


import org.project.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository  extends JpaRepository<Usuario, Integer> {

    // O Spring Boot cria todas as funções de Salvar, Editar e deletar sozinho aqui!
    // Podemos criar buscas personalizadas apenas declarando o nome da função:

    Usuario findByEmail(String email);

}