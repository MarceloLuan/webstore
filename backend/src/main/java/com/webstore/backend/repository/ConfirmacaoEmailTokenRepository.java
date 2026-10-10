package com.webstore.backend.repository;

import com.webstore.backend.model.ConfirmacaoEmailToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ConfirmacaoEmailTokenRepository extends JpaRepository<ConfirmacaoEmailToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ConfirmacaoEmailToken t join fetch t.usuario where t.tokenHash = :tokenHash")
    Optional<ConfirmacaoEmailToken> findForUpdateByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ConfirmacaoEmailToken> findFirstByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
}
