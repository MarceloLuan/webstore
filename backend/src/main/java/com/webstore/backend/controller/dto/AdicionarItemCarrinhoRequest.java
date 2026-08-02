package com.webstore.backend.controller.dto;

public class AdicionarItemCarrinhoRequest {
    private Long produtoTamanhoId;
    private Integer quantidade;

    public Long getProdutoTamanhoId() {
        return produtoTamanhoId;
    }

    public void setProdutoTamanhoId(Long produtoTamanhoId) {
        this.produtoTamanhoId = produtoTamanhoId;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }
}
