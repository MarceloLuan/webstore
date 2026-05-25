package com.webstore.backend.service;

import com.webstore.backend.controller.dto.AtualizarContaRequest;
import com.webstore.backend.controller.dto.UsuarioResponse;
import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Usuario;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
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
    public UsuarioResponse buscarConta(Long id) {
        return UsuarioResponse.from(buscarUsuarioAtivo(id));
    }

    public UsuarioResponse atualizarConta(Long id, AtualizarContaRequest request) {
        validarRequest(request);

        Usuario usuario = buscarUsuarioAtivo(id);
        String emailNormalizado = normalizarEmail(request.getEmail());

        usuarioRepository.findByEmail(emailNormalizado)
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário cadastrado com este e-mail.");
                });

        usuario.setNome(request.getNome().trim());
        usuario.setEmail(emailNormalizado);
        usuario.setTelefone(request.getTelefone().trim());

        if (StringUtils.hasText(request.getSenha())) {
            usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        }

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    public void deletarConta(Long id) {
        Usuario usuario = buscarUsuarioAtivo(id);

        if (usuario instanceof Administrador) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrador não pode excluir a própria conta.");
        }

        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }

    private Usuario buscarUsuarioAtivo(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));

        if (!usuario.getAtivo()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
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
}
