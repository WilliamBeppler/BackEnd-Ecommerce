package org.project.repository;

import org.project.model.PedidoHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoHistoricoRepository extends JpaRepository<PedidoHistorico, Integer> {

    List<PedidoHistorico> findByPedidoIdOrderByDataDescIdDesc(int pedidoId);
}
