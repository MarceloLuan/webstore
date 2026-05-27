package com.webstore.backend.controller.dto;

import com.webstore.backend.model.ProdutoTamanho;

import java.math.BigDecimal;

public class TamanhoResponse {
    private Long id;
    private String tamanho;
    private Integer quantidade;
    private BigDecimal preco;

    public TamanhoResponse() {
    }

    public TamanhoResponse(Long id, String tamanho, Integer quantidade, BigDecimal preco) {
        this.id = id;
        this.tamanho = tamanho;
        this.quantidade = quantidade;
        this.preco = preco;
    }

    public static TamanhoResponse from(ProdutoTamanho pt) {
        return new TamanhoResponse(
                pt.getId(),
                pt.getTamanho() == null ? null : pt.getTamanho().getLabel(),
                pt.getQuantidade(),
                pt.getPreco()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTamanho() {
        return tamanho;
    }

    public void setTamanho(String tamanho) {
        this.tamanho = tamanho;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }
}

