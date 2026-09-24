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
    @Query("select p.id from Pedido p where p.reservaStatus = com.webstore.backend.model.ReservaStatus.ATIVA and p.reservaExpiraEm <= :agora order by p.id")
    List<Long> findReservasVencidas(@Param("agora") java.time.Instant agora, org.springframework.data.domain.Pageable pageable);

    List<Pedido> findAllByStatusOrderByCriadoEmDesc(com.webstore.backend.model.PedidoStatus status);
    @Query("select p.id from Pedido p where p.checkoutFingerprint = :fingerprint")
    Optional<Long> findIdByCheckoutFingerprint(@Param("fingerprint") String fingerprint);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> findWithItensForUpdateById(@Param("id") Long id);

    Optional<Pedido> findByIdAndClienteId(Long id, Long clienteId);

    @EntityGraph(attributePaths = {"itens"})
    List<Pedido> findAllByClienteIdOrderByCriadoEmDesc(Long clienteId);
}
