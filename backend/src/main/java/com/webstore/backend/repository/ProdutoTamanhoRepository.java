package com.webstore.backend.repository;

import com.webstore.backend.model.ProdutoTamanho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProdutoTamanhoRepository extends JpaRepository<ProdutoTamanho, Long> {
    List<ProdutoTamanho> findByProdutoIdAndAtivoTrue(Long produtoId);
}

