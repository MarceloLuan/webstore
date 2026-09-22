package com.webstore.backend.repository;

import com.webstore.backend.model.Pedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> findWithItensForUpdateById(@Param("id") Long id);

    Optional<Pedido> findByIdAndClienteId(Long id, Long clienteId);

    @EntityGraph(attributePaths = {"itens"})
    List<Pedido> findAllByClienteIdOrderByCriadoEmDesc(Long clienteId);
}
