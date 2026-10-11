package com.webstore.backend.service;

import com.webstore.backend.model.RecuperacaoSenhaToken;
import com.webstore.backend.model.Usuario;
import com.webstore.backend.repository.RecuperacaoSenhaTokenRepository;
import com.webstore.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
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

@Service
@Transactional
public class RecuperacaoSenhaService {
    private static final Logger LOG = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RecuperacaoSenhaTokenRepository tokens;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final EmailRecuperacaoSenhaMailer mailer;
    private final Duration expiracao;
    private final Duration intervaloReenvio;
    private final String frontendUrl;

    public RecuperacaoSenhaService(RecuperacaoSenhaTokenRepository tokens, UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder, EmailRecuperacaoSenhaMailer mailer,
            @Value("${email.recuperacao.expiracao-minutos:30}") long minutosExpiracao,
            @Value("${email.recuperacao.reenvio-segundos:60}") long segundosReenvio,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.tokens = tokens; this.usuarios = usuarios; this.passwordEncoder = passwordEncoder; this.mailer = mailer;
        this.expiracao = Duration.ofMinutes(Math.max(1, minutosExpiracao));
        this.intervaloReenvio = Duration.ofSeconds(Math.max(30, segundosReenvio));
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void solicitar(String email) {
        String normalizado;
        try { normalizado = DadosContaValidator.email(email); }
        catch (ResponseStatusException invalido) { throw invalido; }
        Usuario usuario = usuarios.findByEmailForUpdate(normalizado).orElse(null);
        if (usuario == null || !Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.possuiSenhaLocal()) return;
        Instant agora = Instant.now();
        RecuperacaoSenhaToken anterior = tokens.findFirstByUsuarioIdOrderByCriadoEmDesc(usuario.getId()).orElse(null);
        if (anterior != null && anterior.getCriadoEm().plus(intervaloReenvio).isAfter(agora)) return;
        if (anterior != null && anterior.disponivelEm(agora)) anterior.revogar(agora);
        String token = novoToken();
        tokens.save(new RecuperacaoSenhaToken(usuario, hash(token), agora, agora.plus(expiracao)));
        try {
            mailer.enviar(usuario.getEmail(), frontendUrl + "/redefinir-senha?token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8));
        } catch (RuntimeException falha) {
            LOG.warn("Falha ao enviar e-mail de recuperação de senha solicitado.");
        }
    }

    public void redefinir(String token, String senha, String confirmacaoSenha) {
        if (token == null || token.isBlank() || token.length() > 200) throw tokenInvalido();
        DadosContaValidator.novaSenha(senha);
        DadosContaValidator.confirmacaoSenha(senha, confirmacaoSenha);
        RecuperacaoSenhaToken registro = tokens.findForUpdateByTokenHash(hash(token)).orElseThrow(this::tokenInvalido);
        Instant agora = Instant.now();
        if (!registro.disponivelEm(agora) || !Boolean.TRUE.equals(registro.getUsuario().getAtivo())) throw tokenInvalido();
        Usuario usuario = registro.getUsuario();
        if (!usuario.possuiSenhaLocal()) throw tokenInvalido();
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.invalidarSessoes(agora);
        registro.utilizar(agora);
        tokens.findAllByUsuarioIdAndUtilizadoEmIsNullAndRevogadoEmIsNull(usuario.getId())
                .forEach(outro -> { if (outro != registro) outro.revogar(agora); });
    }

    private static String novoToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossivel) { throw new IllegalStateException(impossivel); }
    }
    private ResponseStatusException tokenInvalido() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link inválido, expirado ou já utilizado."); }
}
