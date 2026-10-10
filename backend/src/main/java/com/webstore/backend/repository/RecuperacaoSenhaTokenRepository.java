package com.webstore.backend.repository;

import com.webstore.backend.model.RecuperacaoSenhaToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecuperacaoSenhaTokenRepository extends JpaRepository<RecuperacaoSenhaToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RecuperacaoSenhaToken t join fetch t.usuario where t.tokenHash = :tokenHash")
    Optional<RecuperacaoSenhaToken> findForUpdateByTokenHash(String tokenHash);
    Optional<RecuperacaoSenhaToken> findFirstByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<RecuperacaoSenhaToken> findAllByUsuarioIdAndUtilizadoEmIsNullAndRevogadoEmIsNull(Long usuarioId);
}
