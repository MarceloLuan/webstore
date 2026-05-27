package com.webstore.backend.controller.dto;

import com.webstore.backend.model.Categoria;
import com.webstore.backend.model.Tamanho;

import java.util.List;

public class ProdutoOpcoesResponse {

    private List<String> categorias;
    private List<String> tamanhos;

    public static ProdutoOpcoesResponse fromEnums() {
        ProdutoOpcoesResponse response = new ProdutoOpcoesResponse();
        response.setCategorias(java.util.Arrays.stream(Categoria.values())
                .map(Categoria::getLabel)
                .toList());
        response.setTamanhos(java.util.Arrays.stream(Tamanho.values())
                .map(Tamanho::getLabel)
                .toList());
        return response;
    }

    public List<String> getCategorias() {
        return categorias;
    }

    public void setCategorias(List<String> categorias) {
        this.categorias = categorias;
    }

    public List<String> getTamanhos() {
        return tamanhos;
    }

    public void setTamanhos(List<String> tamanhos) {
        this.tamanhos = tamanhos;
    }
}