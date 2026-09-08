package com.webstore.backend.controller.dto;

import com.webstore.backend.model.PedidoStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(Long id, PedidoStatus status, BigDecimal total,
                             LocalDateTime criadoEm, List<ItemPedidoResponse> itens) {
}
