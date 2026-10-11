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
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

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
        String emailNormalizado = DadosContaValidator.email(request.getEmail());

        usuarioRepository.findByEmail(emailNormalizado)
                .filter(outro -> !outro.getId().equals(usuario.getId()))
                .ifPresent(outro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário cadastrado com este e-mail.");
                });

        usuario.setNome(DadosContaValidator.nome(request.getNome()));
        usuario.setEmail(emailNormalizado);
        usuario.setTelefone(DadosContaValidator.telefone(request.getTelefone()));

        if (temDadosDeSenha(request)) {
            validarTrocaSenha(request, usuario);
            usuario.setSenha(passwordEncoder.encode(request.getSenha()));
            usuario.invalidarSessoes(Instant.now());
        }

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    public void deletarConta() {
        Usuario usuario = buscarUsuarioAutenticado();

        if (usuario instanceof Administrador) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrador não pode excluir a própria conta.");
        }

        usuario.setAtivo(false);
        usuario.invalidarSessoes(Instant.now());
        usuarioRepository.save(usuario);
    }

    public void encerrarSessoes() {
        Usuario usuario = buscarUsuarioAutenticado();
        usuario.invalidarSessoes(Instant.now());
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

        Usuario usuario = usuarioRepository.findByEmail(DadosContaValidator.email(email))
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
        DadosContaValidator.nome(request.getNome());
        DadosContaValidator.email(request.getEmail());
        DadosContaValidator.telefone(request.getTelefone());
    }

    private boolean temDadosDeSenha(AtualizarContaRequest request) {
        return org.springframework.util.StringUtils.hasText(request.getSenhaAtual())
                || org.springframework.util.StringUtils.hasText(request.getSenha())
                || org.springframework.util.StringUtils.hasText(request.getConfirmacaoSenha());
    }

    private void validarTrocaSenha(AtualizarContaRequest request, Usuario usuario) {
        if (!usuario.possuiSenhaLocal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta conta utiliza login externo e não possui senha local.");
        }
        if (!org.springframework.util.StringUtils.hasText(request.getSenhaAtual())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe sua senha atual para trocar a senha.");
        }
        if (!passwordEncoder.matches(request.getSenhaAtual(), usuario.getSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível confirmar a senha atual.");
        }
        DadosContaValidator.novaSenha(request.getSenha());
        DadosContaValidator.confirmacaoSenha(request.getSenha(), request.getConfirmacaoSenha());
    }
}
