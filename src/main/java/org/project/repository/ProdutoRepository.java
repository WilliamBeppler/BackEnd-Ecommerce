package org.project.repository;

import org.project.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Integer> {

    // Vitrine: só o que está à venda. O Spring monta o SQL a partir do nome do método.
    List<Produto> findByAtivoTrue();
}
