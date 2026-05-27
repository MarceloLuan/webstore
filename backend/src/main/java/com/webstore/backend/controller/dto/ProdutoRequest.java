package com.webstore.backend.controller.dto;

public class ProdutoRequest {

    private String nome;
    private String preco;
    private String destaque;
    private String imagem;
    private String descricao;
    private Boolean ativo;
    private String categoria;
    private java.util.List<TamanhoRequest> tamanhos;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getPreco() {
        return preco;
    }

    public void setPreco(String preco) {
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

    public java.util.List<TamanhoRequest> getTamanhos() {
        return tamanhos;
    }

    public void setTamanhos(java.util.List<TamanhoRequest> tamanhos) {
        this.tamanhos = tamanhos;
    }
}

