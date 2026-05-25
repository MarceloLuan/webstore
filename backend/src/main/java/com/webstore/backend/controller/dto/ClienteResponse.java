package com.webstore.backend.controller.dto;

import com.webstore.backend.model.Cliente;
import com.webstore.backend.model.Usuario;

public class ClienteResponse {

    private Long id;
    private String nome;
    private String email;
    private String telefone;

    public ClienteResponse() {
    }

    public ClienteResponse(Long id, String nome, String email, String telefone) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
    }

    public static ClienteResponse from(Cliente cliente) {
        return new ClienteResponse(cliente.getId(), cliente.getNome(), cliente.getEmail(), cliente.getTelefone());
    }

    public static ClienteResponse from(Usuario usuario) {
        return new ClienteResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone());
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }
}

