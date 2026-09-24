package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.CheckoutResponse;
import com.webstore.backend.controller.dto.TentativaPagamentoResponse;
import com.webstore.backend.service.PagamentoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import com.webstore.backend.controller.dto.PedidoResponse;
import java.util.List;

@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {
    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CLIENTE')")
    public CheckoutResponse criarCheckout(@RequestBody com.webstore.backend.controller.dto.CheckoutRequest request) {
        return pagamentoService.criarCheckout(request);
    }

    @PostMapping("/pedidos/{pedidoId}/tentativas")
    @PreAuthorize("hasRole('CLIENTE')")
    public CheckoutResponse tentarNovamente(@PathVariable Long pedidoId) {
        return pagamentoService.tentarNovamente(pedidoId);
    }

    @GetMapping("/pedidos/{pedidoId}/tentativas")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<TentativaPagamentoResponse> listarTentativas(@PathVariable Long pedidoId) {
        return pagamentoService.listarTentativas(pedidoId);
    }

    @GetMapping("/pedidos/{pedidoId}")
    @PreAuthorize("hasRole('CLIENTE')")
    public PedidoStatusResponse buscarPedido(@PathVariable Long pedidoId) {
        return pagamentoService.buscarPedido(pedidoId);
    }

    @GetMapping("/pedidos")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<PedidoResponse> listarPedidos() {
        return pagamentoService.listarPedidos();
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

    @GetMapping("/retorno/{resultado}/{pedidoId}")
    public ResponseEntity<Void> retorno(
            @PathVariable String resultado,
            @PathVariable Long pedidoId,
            @RequestParam(name = "payment_id", required = false) String paymentId
    ) {
        pagamentoService.processarRetorno(paymentId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, pagamentoService.urlFrontendRetorno(pedidoId, resultado))
                .build();
    }

    @SuppressWarnings("unchecked")
    private String obterDataId(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) return null;
        Object id = data.get("id");
        return id == null ? null : id.toString();
    }
}
