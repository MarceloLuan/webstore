package com.webstore.backend.service;

import com.webstore.backend.model.Cliente;
import com.webstore.backend.repository.ClienteRepository;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public Cliente cadastrar(Cliente cliente, String confirmacaoSenha) {
        validarClienteParaCadastro(cliente, confirmacaoSenha);

        String emailNormalizado = DadosContaValidator.email(cliente.getEmail());
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível concluir o cadastro com estes dados.");
        }

        cliente.setId(null);
        cliente.setNome(DadosContaValidator.nome(cliente.getNome()));
        cliente.setEmail(emailNormalizado);
        cliente.setTelefone(DadosContaValidator.telefone(cliente.getTelefone()));
        cliente.setSenha(passwordEncoder.encode(cliente.getSenha()));
        cliente.setAtivo(true);
        cliente.aguardarConfirmacaoEmail();
        return clienteRepository.save(cliente);
    }

    private void validarClienteParaCadastro(Cliente cliente, String confirmacaoSenha) {
        if (cliente == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do cliente são obrigatórios.");
        }
        DadosContaValidator.nome(cliente.getNome());
        DadosContaValidator.email(cliente.getEmail());
        DadosContaValidator.telefone(cliente.getTelefone());
        DadosContaValidator.novaSenha(cliente.getSenha());
        DadosContaValidator.confirmacaoSenha(cliente.getSenha(), confirmacaoSenha);
    }
}


