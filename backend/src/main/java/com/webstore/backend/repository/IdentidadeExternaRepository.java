package com.webstore.backend.repository;

import com.webstore.backend.model.IdentidadeExterna;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdentidadeExternaRepository extends JpaRepository<IdentidadeExterna, Long> {
    @EntityGraph(attributePaths = "usuario")
    Optional<IdentidadeExterna> findByProvedorAndSubject(IdentidadeExterna.Provedor provedor, String subject);
}
