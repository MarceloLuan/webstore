package com.webstore.backend.service;

import com.webstore.backend.model.*;
import com.webstore.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleLoginService {
    private final UsuarioRepository usuarios;
    private final IdentidadeExternaRepository identidades;
    private final RateLimitService limits;

    public GoogleLoginService(UsuarioRepository usuarios, IdentidadeExternaRepository identidades, RateLimitService limits) {
        this.usuarios = usuarios;
        this.identidades = identidades;
        this.limits = limits;
    }

    @Transactional
    public Usuario encontrarOuCriar(Jwt claims, String ip) {
        var identidade = identidades.findByProvedorAndSubject(IdentidadeExterna.Provedor.GOOGLE, claims.getSubject());
        if (identidade.isPresent()) {
            Usuario usuario = identidade.get().getUsuario();
            if (!Boolean.TRUE.equals(usuario.getAtivo())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta indisponível.");
            }
            // Email/name changes at Google must not change this account or its permissions.
            return usuario;
        }
        if (!Boolean.TRUE.equals(claims.getClaimAsBoolean("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Confirme seu e-mail no Google antes de continuar.");
        }
        String email = DadosContaValidator.email(claims.getClaimAsString("email"));
        if (usuarios.existsByEmail(email)) throw conflito();
        limits.verificarCadastro(ip);
        Cliente usuario = new Cliente();
        String nome = claims.getClaimAsString("name");
        usuario.setNome(nome == null || nome.isBlank() ? "Cliente" : nome.strip().substring(0, Math.min(nome.strip().length(), 120)));
        usuario.setEmail(email);
        usuario.setAtivo(true);
        // Google is authoritative for Gmail and verified Workspace addresses.
        String dominio = claims.getClaimAsString("hd");
        if (email.endsWith("@gmail.com") || (dominio != null && !dominio.isBlank())) usuario.confirmarEmail();
        else usuario.aguardarConfirmacaoEmail();
        usuarios.saveAndFlush(usuario);
        identidades.saveAndFlush(new IdentidadeExterna(usuario, IdentidadeExterna.Provedor.GOOGLE, claims.getSubject()));
        return usuario;
    }

    public static ResponseStatusException conflito() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Não foi possível cadastrar este acesso Google. Entre pelo método já cadastrado. O vínculo automático está desativado.");
    }
}
