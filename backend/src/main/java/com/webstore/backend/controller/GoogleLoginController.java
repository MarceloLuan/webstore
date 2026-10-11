package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.*;
import com.webstore.backend.model.Usuario;
import com.webstore.backend.security.*;
import com.webstore.backend.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GoogleLoginController {
    private final GoogleCredentialVerifier verifier;
    private final GoogleLoginService login;
    private final UsuarioDetailsService details;
    private final JwtUtil jwt;
    private final RateLimitService limits;
    private final ConfirmacaoEmailService confirmacao;

    public GoogleLoginController(GoogleCredentialVerifier verifier, GoogleLoginService login,
            UsuarioDetailsService details, JwtUtil jwt, RateLimitService limits, ConfirmacaoEmailService confirmacao) {
        this.verifier = verifier; this.login = login; this.details = details;
        this.jwt = jwt; this.limits = limits; this.confirmacao = confirmacao;
    }

    public record GoogleLoginRequest(String credential) {
        @Override public String toString() { return "GoogleLoginRequest[credential=REDACTED]"; }
    }

    @PostMapping(value = "/api/login/google", consumes = "application/json")
    public LoginResponse login(@RequestBody GoogleLoginRequest request, HttpServletRequest http) {
        limits.verificarGoogleIp(http.getRemoteAddr());
        var claims = verifier.verify(request == null ? null : request.credential());
        limits.verificarGoogleIdentidade(claims.getSubject());
        Usuario usuario;
        try {
            usuario = login.encontrarOuCriar(claims, http.getRemoteAddr());
        } catch (DataIntegrityViolationException conflict) {
            // Concurrent registration must never turn into an email-based account link.
            throw GoogleLoginService.conflito();
        }
        if (!Boolean.TRUE.equals(usuario.getEmailConfirmado())) {
            confirmacao.reenviar(usuario.getEmail());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Confirme seu e-mail pelo link da WebStore e tente entrar com Google novamente.");
        }
        return new LoginResponse(jwt.generateToken(details.from(usuario), usuario.getId(), usuario.getVersaoSessao()),
                UsuarioResponse.from(usuario));
    }
}
