package com.webstore.backend.repository;

import com.webstore.backend.model.ItemCarrinho;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCarrinhoRepository extends JpaRepository<ItemCarrinho, Long> {
    void deleteByProdutoTamanhoId(Long produtoTamanhoId);

    void deleteByProdutoTamanhoProdutoId(Long produtoId);
}
