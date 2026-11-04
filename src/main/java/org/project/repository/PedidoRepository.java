package org.project.repository;

import org.project.model.Pedido;
import org.project.model.StatusPagamento;
import org.project.model.StatusProcessamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

    @Query("""
            SELECT DISTINCT p FROM Pedido p
              LEFT JOIN FETCH p.itens i
              LEFT JOIN FETCH i.produto
            WHERE p.usuario.id = :usuarioId
            ORDER BY p.dataPedido DESC, p.id DESC
            """)
    java.util.List<Pedido> buscarDoClienteComItens(@Param("usuarioId") int usuarioId);

    @Query("""
            SELECT p FROM Pedido p JOIN p.usuario u
            WHERE (:pagamento IS NULL OR p.statusPagamento = :pagamento)
              AND (:processamento IS NULL OR p.statusProcessamento = :processamento)
              AND (:id IS NULL OR p.id = :id)
              AND (:texto IS NULL OR LOWER(u.nome) LIKE :texto OR LOWER(u.email) LIKE :texto)
            """)
    Page<Pedido> buscarParaPainel(@Param("pagamento") StatusPagamento pagamento,
                                  @Param("processamento") StatusProcessamento processamento,
                                  @Param("id") Integer id,
                                  @Param("texto") String texto,
                                  Pageable pagina);

    @Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.itens WHERE p.id = :id")
    java.util.Optional<Pedido> buscarComItens(@Param("id") int id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    java.util.Optional<Pedido> buscarParaAtualizar(@Param("id") int id);

    long countByStatusPagamento(StatusPagamento statusPagamento);

    long countByStatusPagamentoAndStatusProcessamento(StatusPagamento statusPagamento,
                                                      StatusProcessamento statusProcessamento);
}
