package com.webstore.backend.service;

import com.webstore.backend.model.Cliente;
import com.webstore.backend.model.Usuario;
import com.webstore.backend.repository.ClienteRepository;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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

    @Transactional(readOnly = true)
    public Usuario login(String email, String senha) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(senha)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail e senha são obrigatórios.");
        }

        Usuario usuario = usuarioRepository.findByEmail(normalizarEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."));

        if (!usuario.getAtivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário inativo.");
        }

        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }

        return usuario;
    }
    @Transactional(readOnly = true)
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll()
                .stream()
                .filter(Cliente::getAtivo)
                .toList();
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
        
        if (!cliente.getAtivo()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        }
        
        return cliente;
    }

    public Cliente atualizar(Long id, Cliente clienteAtualizado) {
        validarClienteParaAtualizacao(clienteAtualizado);

        Cliente clienteExistente = buscarPorId(id);
        
        // Verifica se o cliente está inativo (soft deleted)
        if (!clienteExistente.getAtivo()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        }
        
        String emailNormalizado = normalizarEmail(clienteAtualizado.getEmail());

        usuarioRepository.findByEmail(emailNormalizado)
                .filter(usuario -> !usuario.getId().equals(id))
                .ifPresent(usuario -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário cadastrado com este e-mail.");
                });

        clienteExistente.setNome(clienteAtualizado.getNome().trim());
        clienteExistente.setEmail(emailNormalizado);
        clienteExistente.setTelefone(clienteAtualizado.getTelefone().trim());

        if (StringUtils.hasText(clienteAtualizado.getSenha())) {
            clienteExistente.setSenha(passwordEncoder.encode(clienteAtualizado.getSenha()));
        }

        return clienteRepository.save(clienteExistente);
    }

    public void deletar(Long id) {
        Cliente clienteExistente = buscarPorId(id);
        
        // Soft delete: apenas marcar como inativo ao invés de deletar
        clienteExistente.setAtivo(false);
        clienteRepository.save(clienteExistente);
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

    private void validarClienteParaAtualizacao(Cliente cliente) {
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
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }
}


