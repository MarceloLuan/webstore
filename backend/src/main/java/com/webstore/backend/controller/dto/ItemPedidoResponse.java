package com.webstore.backend.controller.dto;

import java.math.BigDecimal;

public record ItemPedidoResponse(String nome, String tamanho, int quantidade, BigDecimal precoUnitario) {
}
