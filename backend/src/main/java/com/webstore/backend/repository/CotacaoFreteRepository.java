package com.webstore.backend.repository;
import com.webstore.backend.model.CotacaoFrete;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CotacaoFreteRepository extends JpaRepository<CotacaoFrete, UUID> {}
