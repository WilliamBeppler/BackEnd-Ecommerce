package org.project.repository;

import org.project.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface ProdutoRepository extends JpaRepository<Produto, Integer> {

}
