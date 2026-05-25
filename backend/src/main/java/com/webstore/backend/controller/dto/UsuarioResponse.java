package com.webstore.backend.controller.dto;

import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Usuario;

public class UsuarioResponse {

    private Long id;
    private String nome;
    private String email;
    private String telefone;
    private String role;
    private Boolean ativo;

    public UsuarioResponse() {
    }

    public UsuarioResponse(Long id, String nome, String email, String telefone, String role, Boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.role = role;
        this.ativo = ativo;
    }

    public static UsuarioResponse from(Usuario usuario) {
        String role = usuario instanceof Administrador ? "ADMIN" : "CLIENTE";
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone(), role, usuario.getAtivo());
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }
}

