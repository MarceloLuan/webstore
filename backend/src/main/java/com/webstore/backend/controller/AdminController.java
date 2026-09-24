package com.webstore.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final com.webstore.backend.service.PagamentoService pagamentos;
    public AdminController(com.webstore.backend.service.PagamentoService pagamentos) { this.pagamentos = pagamentos; }

    @GetMapping("/pedidos/pendencias-estoque")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<com.webstore.backend.controller.dto.PedidoStatusResponse> pendenciasEstoque() {
        return pagamentos.listarPendenciasEstoque();
    }

    @GetMapping("/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("message", "admin-ok"));
    }
}

