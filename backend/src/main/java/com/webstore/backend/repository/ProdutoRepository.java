package com.webstore.backend.repository;

import com.webstore.backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    List<Produto> findAllByAtivoTrueOrderByIdDesc();

    List<Produto> findAllByOrderByIdDesc();

    Optional<Produto> findByCodigo(String codigo);
}

