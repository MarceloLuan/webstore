package com.webstore.backend.controller.dto;

public class LoginResponse {

    private String token;
    private UsuarioResponse user;

    public LoginResponse() {
    }

    public LoginResponse(String token, UsuarioResponse user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UsuarioResponse getUser() {
        return user;
    }

    public void setUser(UsuarioResponse user) {
        this.user = user;
    }
}

