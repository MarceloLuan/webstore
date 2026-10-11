package com.webstore.backend.service;

import com.webstore.backend.model.ConfirmacaoEmailToken;
import com.webstore.backend.model.Usuario;
import com.webstore.backend.repository.ConfirmacaoEmailTokenRepository;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional
public class ConfirmacaoEmailService {
    private static final Logger LOG = LoggerFactory.getLogger(ConfirmacaoEmailService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ConfirmacaoEmailTokenRepository tokens;
    private final UsuarioRepository usuarios;
    private final EmailConfirmacaoMailer mailer;
    private final Duration expiracao;
    private final Duration intervaloReenvio;
    private final String frontendUrl;

    public ConfirmacaoEmailService(ConfirmacaoEmailTokenRepository tokens, UsuarioRepository usuarios,
                                   EmailConfirmacaoMailer mailer,
                                   @Value("${email.confirmacao.expiracao-minutos:60}") long minutosExpiracao,
                                   @Value("${email.confirmacao.reenvio-segundos:60}") long segundosReenvio,
                                   @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.tokens = tokens;
        this.usuarios = usuarios;
        this.mailer = mailer;
        this.expiracao = Duration.ofMinutes(Math.max(1, minutosExpiracao));
        this.intervaloReenvio = Duration.ofSeconds(Math.max(30, segundosReenvio));
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void enviarParaNovoCadastro(Usuario usuario) {
        emitir(usuario, false);
    }

    public void reenviar(String email) {
        String emailNormalizado;
        try {
            emailNormalizado = DadosContaValidator.email(email);
        } catch (ResponseStatusException invalido) {
            return;
        }
        Usuario usuario = usuarios.findByEmailForUpdate(emailNormalizado).orElse(null);
        if (usuario == null || Boolean.TRUE.equals(usuario.getEmailConfirmado())) return;
        try {
            emitir(usuario, true);
        } catch (RuntimeException falha) {
            LOG.warn("Falha ao enviar e-mail de confirmação solicitado.");
        }
    }

    public void confirmar(String token) {
        if (token == null || token.length() > 200) throw tokenInvalido();
        ConfirmacaoEmailToken registro = tokens.findForUpdateByTokenHash(hash(token))
                .orElseThrow(this::tokenInvalido);
        Instant agora = Instant.now();
        if (!registro.disponivelEm(agora) || Boolean.TRUE.equals(registro.getUsuario().getEmailConfirmado())) throw tokenInvalido();
        registro.utilizar(agora);
        registro.getUsuario().confirmarEmail();
    }

    private void emitir(Usuario usuario, boolean respeitarIntervalo) {
        Instant agora = Instant.now();
        ConfirmacaoEmailToken anterior = tokens.findFirstByUsuarioIdOrderByCriadoEmDesc(usuario.getId()).orElse(null);
        if (respeitarIntervalo && anterior != null && anterior.getCriadoEm().plus(intervaloReenvio).isAfter(agora)) {
            return;
        }
        if (anterior != null && anterior.disponivelEm(agora)) anterior.revogar(agora);
        String token = novoToken();
        tokens.save(new ConfirmacaoEmailToken(usuario, hash(token), agora, agora.plus(expiracao)));
        mailer.enviar(usuario.getEmail(), frontendUrl + "/confirmar-email?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

    private String novoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponível.", erro);
        }
    }

    private ResponseStatusException tokenInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Este link de confirmação é inválido, expirou ou já foi utilizado.");
    }
}
