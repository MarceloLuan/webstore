package com.webstore.backend.controller.dto;

import com.webstore.backend.model.Produto;

import java.math.BigDecimal;

public class ProdutoResponse {

    private Long id;
    private String nome;
    private BigDecimal preco;
    private String destaque;
    private String imagem;
    private String descricao;
    private Boolean ativo;
    private String categoria;
    private java.util.List<TamanhoResponse> tamanhos;

    public ProdutoResponse() {
    }

    public ProdutoResponse(Long id, String nome, BigDecimal preco, String destaque, String imagem, String descricao, Boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.preco = preco;
        this.destaque = destaque;
        this.imagem = imagem;
        this.descricao = descricao;
        this.ativo = ativo;
    }

    public static ProdutoResponse from(Produto produto) {
        ProdutoResponse resp = new ProdutoResponse(
                produto.getId(),
                produto.getNome(),
                produto.getPreco(),
                produto.getDestaque(),
                produto.getImagem(),
                produto.getDescricao(),
                produto.getAtivo()
        );
        if (produto.getCategoria() != null) {
            resp.setCategoria(produto.getCategoria().getLabel());
        }
        if (produto.getTamanhos() != null) {
            java.util.List<TamanhoResponse> list = new java.util.ArrayList<>();
            for (com.webstore.backend.model.ProdutoTamanho pt : produto.getTamanhos()) {
                if (pt.getAtivo() == null || pt.getAtivo()) {
                    list.add(TamanhoResponse.from(pt));
                }
            }
            resp.setTamanhos(list);
        }
        return resp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public String getDestaque() {
        return destaque;
    }

    public void setDestaque(String destaque) {
        this.destaque = destaque;
    }

    public String getImagem() {
        return imagem;
    }

    public void setImagem(String imagem) {
        this.imagem = imagem;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public java.util.List<TamanhoResponse> getTamanhos() {
        return tamanhos;
    }

    public void setTamanhos(java.util.List<TamanhoResponse> tamanhos) {
        this.tamanhos = tamanhos;
    }
}

