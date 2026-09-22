package com.webstore.backend.repository;

import com.webstore.backend.model.ProdutoTamanho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProdutoTamanhoRepository extends JpaRepository<ProdutoTamanho, Long> {
    @Modifying
    @Query("update ProdutoTamanho t set t.quantidade = t.quantidade - :quantidade where t.id = :id and t.quantidade >= :quantidade")
    int baixarSeDisponivel(@Param("id") Long id, @Param("quantidade") int quantidade);
    List<ProdutoTamanho> findByProdutoIdAndAtivoTrue(Long produtoId);
}

