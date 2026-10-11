package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.ConfirmarEmailRequest;
import com.webstore.backend.controller.dto.ReenviarConfirmacaoEmailRequest;
import com.webstore.backend.service.ConfirmacaoEmailService;
import com.webstore.backend.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/confirmacao-email")
public class ConfirmacaoEmailController {
    private final ConfirmacaoEmailService confirmacoes;
    private final RateLimitService rateLimit;

    public ConfirmacaoEmailController(ConfirmacaoEmailService confirmacoes, RateLimitService rateLimit) {
        this.confirmacoes = confirmacoes;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/confirmar")
    public ResponseEntity<Map<String, String>> confirmar(@RequestBody ConfirmarEmailRequest request) {
        confirmacoes.confirmar(request == null ? null : request.token());
        return ResponseEntity.ok(Map.of("message", "E-mail confirmado com sucesso. Agora você pode entrar na sua conta."));
    }

    @PostMapping("/reenviar")
    public ResponseEntity<Map<String, String>> reenviar(@RequestBody ReenviarConfirmacaoEmailRequest request,
                                                        HttpServletRequest httpRequest) {
        String email = request == null ? null : request.email();
        rateLimit.verificarReenvioConfirmacao(httpRequest.getRemoteAddr(), email);
        confirmacoes.reenviar(email);
        return ResponseEntity.accepted().body(Map.of("message",
                "Se existir uma conta pendente para este e-mail, enviaremos uma nova confirmação."));
    }
}
