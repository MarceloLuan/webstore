package com.webstore.backend.service;

import com.webstore.backend.model.Cliente;
import com.webstore.backend.repository.ClienteRepository;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public ClienteService(ClienteRepository clienteRepository, UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Cliente cadastrar(Cliente cliente) {
        validarClienteParaCadastro(cliente);

        String emailNormalizado = normalizarEmail(cliente.getEmail());
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário cadastrado com este e-mail.");
        }

        cliente.setId(null);
        cliente.setEmail(emailNormalizado);
        cliente.setTelefone(cliente.getTelefone().trim());
        cliente.setSenha(passwordEncoder.encode(cliente.getSenha()));
        cliente.setAtivo(true);
        return clienteRepository.save(cliente);
    }

    private void validarClienteParaCadastro(Cliente cliente) {
        if (cliente == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do cliente são obrigatórios.");
        }
        if (!StringUtils.hasText(cliente.getNome())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome é obrigatório.");
        }
        if (!StringUtils.hasText(cliente.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail é obrigatório.");
        }
        if (!StringUtils.hasText(cliente.getTelefone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone é obrigatório.");
        }
        if (!StringUtils.hasText(cliente.getSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha é obrigatória.");
        }
    }


    private String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }
}


