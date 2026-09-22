package com.webstore.backend.controller.dto;

import com.webstore.backend.model.PedidoStatus;
import java.time.LocalDateTime;

public record TentativaPagamentoResponse(Long id, PedidoStatus status, String statusProvedor,
        boolean concluiuCompra, boolean aprovacaoDuplicada, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {}
