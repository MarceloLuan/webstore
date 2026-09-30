package com.webstore.backend.service.frete;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class FreteException extends ResponseStatusException {
    private final String codigo;
    public FreteException(HttpStatus status, String codigo, String mensagem) {
        super(status, mensagem);
        this.codigo = codigo;
    }
    public String getCodigo() { return codigo; }
}
