package com.webstore.backend.controller.dto;

import com.webstore.backend.model.PedidoStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(Long id, PedidoStatus status, BigDecimal total,
                             LocalDateTime criadoEm, List<ItemPedidoResponse> itens,
                             com.webstore.backend.model.ReservaStatus reservaStatus, java.time.Instant reservaExpiraEm,
                             com.webstore.backend.model.ModalidadeRecebimento modalidade,
                             com.webstore.backend.model.EnderecoEntrega enderecoEntrega) {
}
