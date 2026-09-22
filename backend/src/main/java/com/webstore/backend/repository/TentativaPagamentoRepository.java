package com.webstore.backend.repository;

import com.webstore.backend.model.TentativaPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TentativaPagamentoRepository extends JpaRepository<TentativaPagamento, Long> {
    @Query("select t.pedido.id from TentativaPagamento t where t.id = :id")
    Optional<Long> buscarPedidoId(@Param("id") Long id);
    Optional<TentativaPagamento> findByPaymentId(String paymentId);
    List<TentativaPagamento> findAllByPedidoIdOrderByIdDesc(Long pedidoId);
}
