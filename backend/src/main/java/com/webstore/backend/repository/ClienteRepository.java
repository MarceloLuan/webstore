package com.webstore.backend.repository;

import com.webstore.backend.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> findForCheckoutById(@Param("id") Long id);

    Optional<Cliente> findByEmail(String email);

    boolean existsByEmail(String email);
}

