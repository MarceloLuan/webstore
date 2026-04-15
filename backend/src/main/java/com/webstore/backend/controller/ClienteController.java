package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.ClienteCadastroRequest;
import com.webstore.backend.controller.dto.ClienteLoginRequest;
import com.webstore.backend.controller.dto.ClienteResponse;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.service.ClienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping("/clientes")
    public ResponseEntity<ClienteResponse> cadastrar(@RequestBody ClienteCadastroRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNome(request.getNome());
        cliente.setEmail(request.getEmail());
        cliente.setSenha(request.getSenha());

        Cliente clienteSalvo = clienteService.cadastrar(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponse.from(clienteSalvo));
    }

    @PostMapping("/login")
    public ResponseEntity<ClienteResponse> login(@RequestBody ClienteLoginRequest request) {
        Cliente cliente = clienteService.login(request.getEmail(), request.getSenha());
        return ResponseEntity.ok(ClienteResponse.from(cliente));
    }
}

