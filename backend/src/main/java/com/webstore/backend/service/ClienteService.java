package com.webstore.backend.service;

import com.webstore.backend.model.Cliente;
import com.webstore.backend.repository.ClienteRepository;
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
    private final PasswordEncoder passwordEncoder;

    public ClienteService(ClienteRepository clienteRepository, PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Cliente cadastrar(Cliente cliente) {
        validarClienteParaCadastro(cliente);

        String emailNormalizado = normalizarEmail(cliente.getEmail());
        if (clienteRepository.existsByEmail(emailNormalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cliente cadastrado com este e-mail.");
        }

        cliente.setId(null);
        cliente.setEmail(emailNormalizado);
        cliente.setTelefone(cliente.getTelefone().trim());
        cliente.setSenha(passwordEncoder.encode(cliente.getSenha()));
        return clienteRepository.save(cliente);
    }

    @Transactional(readOnly = true)
    public Cliente login(String email, String senha) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(senha)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail e senha são obrigatórios.");
        }

        Cliente cliente = clienteRepository.findByEmail(normalizarEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."));

        if (!passwordEncoder.matches(senha, cliente.getSenha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }

        return cliente;
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    public Cliente atualizar(Long id, Cliente clienteAtualizado) {
        validarClienteParaAtualizacao(clienteAtualizado);

        Cliente clienteExistente = buscarPorId(id);
        String emailNormalizado = normalizarEmail(clienteAtualizado.getEmail());

        clienteRepository.findByEmail(emailNormalizado)
                .filter(cliente -> !cliente.getId().equals(id))
                .ifPresent(cliente -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cliente cadastrado com este e-mail.");
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
        if (!clienteRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        }
        clienteRepository.deleteById(id);
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


