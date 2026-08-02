package com.webstore.backend.controller.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CarrinhoResponse {
    private Long id;
    private List<ItemCarrinhoResponse> itens = new ArrayList<>();
    private Integer quantidadeTotal;
    private BigDecimal subtotal;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<ItemCarrinhoResponse> getItens() {
        return itens;
    }

    public void setItens(List<ItemCarrinhoResponse> itens) {
        this.itens = itens;
    }

    public Integer getQuantidadeTotal() {
        return quantidadeTotal;
    }

    public void setQuantidadeTotal(Integer quantidadeTotal) {
        this.quantidadeTotal = quantidadeTotal;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
