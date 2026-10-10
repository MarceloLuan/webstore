package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.RedefinirSenhaRequest;
import com.webstore.backend.controller.dto.SolicitarRecuperacaoSenhaRequest;
import com.webstore.backend.service.RecuperacaoSenhaRateLimiter;
import com.webstore.backend.service.RecuperacaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/senha")
public class RecuperacaoSenhaController {
    private static final String RESPOSTA_GENERICA = "Se existir uma conta com esse e-mail, enviaremos um link para redefinir a senha.";
    private final RecuperacaoSenhaService recuperacao;
    private final RecuperacaoSenhaRateLimiter limiter;
    public RecuperacaoSenhaController(RecuperacaoSenhaService recuperacao, RecuperacaoSenhaRateLimiter limiter) {
        this.recuperacao = recuperacao; this.limiter = limiter;
    }
    @PostMapping("/esqueci")
    public ResponseEntity<Map<String, String>> solicitar(@RequestBody(required = false) SolicitarRecuperacaoSenhaRequest request,
                                                         HttpServletRequest httpRequest) {
        limiter.verificar(httpRequest.getRemoteAddr());
        recuperacao.solicitar(request == null ? null : request.email());
        return ResponseEntity.ok(Map.of("message", RESPOSTA_GENERICA));
    }
    @PostMapping("/redefinir")
    public ResponseEntity<Map<String, String>> redefinir(@RequestBody(required = false) RedefinirSenhaRequest request) {
        recuperacao.redefinir(request == null ? null : request.token(), request == null ? null : request.senha(),
                request == null ? null : request.confirmacaoSenha());
        return ResponseEntity.ok(Map.of("message", "Senha redefinida. Entre novamente com sua nova senha."));
    }
}
