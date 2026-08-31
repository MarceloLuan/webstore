package com.webstore.backend.controller.dto;

import com.webstore.backend.model.PedidoStatus;

import java.math.BigDecimal;

public record PedidoStatusResponse(Long id, PedidoStatus status, BigDecimal total) {
}
