package com.webstore.backend.repository;

import com.webstore.backend.model.Carrinho;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {

    @EntityGraph(attributePaths = {"itens", "itens.produtoTamanho", "itens.produtoTamanho.produto"})
    Optional<Carrinho> findByClienteId(Long clienteId);
}
