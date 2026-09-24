package com.webstore.backend.controller.dto;

import com.webstore.backend.model.PedidoStatus;

import java.math.BigDecimal;

public record PedidoStatusResponse(Long id, PedidoStatus status, BigDecimal total,
        com.webstore.backend.model.ReservaStatus reservaStatus, java.time.Instant reservaExpiraEm,
        com.webstore.backend.model.ModalidadeRecebimento modalidade,
        com.webstore.backend.model.EnderecoEntrega enderecoEntrega) {
}
