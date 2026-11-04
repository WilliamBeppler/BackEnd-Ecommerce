package org.project.repository;


import org.project.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository  extends JpaRepository<Usuario, Integer> {

    Usuario findByEmail(String email);

    boolean existsByCpf(String cpf);

}