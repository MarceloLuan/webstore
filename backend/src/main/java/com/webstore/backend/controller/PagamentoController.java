package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.CheckoutResponse;
import com.webstore.backend.service.PagamentoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.webstore.backend.controller.dto.PedidoStatusResponse;

@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {
    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CLIENTE')")
    public CheckoutResponse criarCheckout() {
        return pagamentoService.criarCheckout();
    }

    @GetMapping("/pedidos/{pedidoId}")
    @PreAuthorize("hasRole('CLIENTE')")
    public PedidoStatusResponse buscarPedido(@PathVariable Long pedidoId) {
        return pagamentoService.buscarPedido(pedidoId);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String resolvedDataId = dataId != null ? dataId : obterDataId(body);
        pagamentoService.processarWebhook(resolvedDataId, xSignature, xRequestId);
        return ResponseEntity.ok().build();
    }

    @SuppressWarnings("unchecked")
    private String obterDataId(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) return null;
        Object id = data.get("id");
        return id == null ? null : id.toString();
    }
}
