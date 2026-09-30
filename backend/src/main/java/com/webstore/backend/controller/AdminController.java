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
    private final com.webstore.backend.service.AdminPedidoService pedidos;
    public AdminController(com.webstore.backend.service.PagamentoService pagamentos, com.webstore.backend.service.AdminPedidoService pedidos) { this.pagamentos = pagamentos; this.pedidos = pedidos; }

    @GetMapping("/pedidos")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<com.webstore.backend.controller.dto.AdminPedidoResponse> listarPedidos() { return pedidos.listar(); }

    @GetMapping("/pedidos/{pedidoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public com.webstore.backend.controller.dto.AdminPedidoResponse buscarPedido(@org.springframework.web.bind.annotation.PathVariable Long pedidoId) { return pedidos.buscar(pedidoId); }

    @org.springframework.web.bind.annotation.PatchMapping("/pedidos/{pedidoId}/status-entrega")
    @PreAuthorize("hasRole('ADMIN')")
    public com.webstore.backend.controller.dto.AdminPedidoResponse atualizarStatus(@org.springframework.web.bind.annotation.PathVariable Long pedidoId,
            @org.springframework.web.bind.annotation.RequestBody com.webstore.backend.controller.dto.AdminPedidoStatusRequest request) {
        return pedidos.atualizarStatus(pedidoId, request);
    }

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

