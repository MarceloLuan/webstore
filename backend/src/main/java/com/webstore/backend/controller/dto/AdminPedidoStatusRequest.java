package com.webstore.backend.controller.dto;

import com.webstore.backend.model.StatusEntrega;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record AdminPedidoStatusRequest(StatusEntrega statusEntrega, String codigoRastreio) {
    public StatusEntrega validar() {
        if (statusEntrega == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um status operacional válido.");
        return statusEntrega;
    }
    public String rastreioNormalizado() {
        if (codigoRastreio == null) return null;
        String valor = codigoRastreio.strip();
        if (valor.isBlank()) return null;
        if (valor.length() > 80 || valor.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de rastreio inválido.");
        }
        return valor;
    }
}
