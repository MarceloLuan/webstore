package com.webstore.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "itens_carrinho", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_itens_carrinho_variacao",
                columnNames = {"carrinho_id", "produto_tamanho_id"}
        )
})
public class ItemCarrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrinho_id", nullable = false)
    private Carrinho carrinho;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_tamanho_id", nullable = false)
    private ProdutoTamanho produtoTamanho;

    private Integer quantidade;

    public Long getId() {
        return id;
    }

    public Carrinho getCarrinho() {
        return carrinho;
    }

    public void setCarrinho(Carrinho carrinho) {
        this.carrinho = carrinho;
    }

    public ProdutoTamanho getProdutoTamanho() {
        return produtoTamanho;
    }

    public void setProdutoTamanho(ProdutoTamanho produtoTamanho) {
        this.produtoTamanho = produtoTamanho;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }
}
