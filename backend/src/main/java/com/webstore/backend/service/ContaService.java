package com.webstore.backend.service;

import com.webstore.backend.controller.dto.AtualizarContaRequest;
import com.webstore.backend.controller.dto.UsuarioResponse;
import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Usuario;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ContaService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public ContaService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarConta() {
        return UsuarioResponse.from(buscarUsuarioAutenticado());
    }

    public UsuarioResponse atualizarConta(AtualizarContaRequest request) {
        validarRequest(request);

        Usuario usuario = buscarUsuarioAutenticado();
        String emailNormalizado = normalizarEmail(request.getEmail());

        usuarioRepository.findByEmail(emailNormalizado)
                .filter(outro -> !outro.getId().equals(usuario.getId()))
                .ifPresent(outro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário cadastrado com este e-mail.");
                });

        usuario.setNome(request.getNome().trim());
        usuario.setEmail(emailNormalizado);
        usuario.setTelefone(request.getTelefone().trim());

        if (StringUtils.hasText(request.getSenha())) {
            validarTrocaSenha(request, usuario);
            usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        }

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    public void deletarConta() {
        Usuario usuario = buscarUsuarioAutenticado();

        if (usuario instanceof Administrador) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrador não pode excluir a própria conta.");
        }

        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }

    private Usuario buscarUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        }

        String email = authentication.getName();

        Usuario usuario = usuarioRepository.findByEmail(normalizarEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado."));

        if (!usuario.getAtivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário inativo.");
        }

        return usuario;
    }

    private void validarRequest(AtualizarContaRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados da conta são obrigatórios.");
        }
        if (!StringUtils.hasText(request.getNome())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome é obrigatório.");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail é obrigatório.");
        }
        if (!StringUtils.hasText(request.getTelefone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone é obrigatório.");
        }
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void validarTrocaSenha(AtualizarContaRequest request, Usuario usuario) {
        if (!StringUtils.hasText(request.getSenhaAtual())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe sua senha atual para trocar a senha.");
        }
        if (!passwordEncoder.matches(request.getSenhaAtual(), usuario.getSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha atual incorreta.");
        }
        if (!StringUtils.hasText(request.getConfirmacaoSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirme a nova senha.");
        }
        if (!request.getSenha().equals(request.getConfirmacaoSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nova senha e a confirmacao precisam ser iguais.");
        }
    }
}
