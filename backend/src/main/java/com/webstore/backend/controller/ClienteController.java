package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.ClienteCadastroRequest;
import com.webstore.backend.controller.dto.ClienteLoginRequest;
import com.webstore.backend.controller.dto.ClienteResponse;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.service.ClienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        cliente.setTelefone(request.getTelefone());
        cliente.setSenha(request.getSenha());

        Cliente clienteSalvo = clienteService.cadastrar(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponse.from(clienteSalvo));
    }

    @PostMapping("/login")
    public ResponseEntity<ClienteResponse> login(@RequestBody ClienteLoginRequest request) {
        Cliente cliente = clienteService.login(request.getEmail(), request.getSenha());
        return ResponseEntity.ok(ClienteResponse.from(cliente));
    }

    @GetMapping("/clientes")
    public ResponseEntity<List<ClienteResponse>> listarTodos() {
        List<ClienteResponse> clientes = clienteService.listarTodos()
                .stream()
                .map(ClienteResponse::from)
                .toList();
        return ResponseEntity.ok(clientes);
    }

    @GetMapping("/clientes/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
        Cliente cliente = clienteService.buscarPorId(id);
        return ResponseEntity.ok(ClienteResponse.from(cliente));
    }

    @PutMapping("/clientes/{id}")
    public ResponseEntity<ClienteResponse> atualizar(@PathVariable Long id,
                                                     @RequestBody ClienteCadastroRequest request) {
        Cliente clienteAtualizado = new Cliente();
        clienteAtualizado.setNome(request.getNome());
        clienteAtualizado.setEmail(request.getEmail());
        clienteAtualizado.setTelefone(request.getTelefone());
        clienteAtualizado.setSenha(request.getSenha());

        Cliente clienteSalvo = clienteService.atualizar(id, clienteAtualizado);
        return ResponseEntity.ok(ClienteResponse.from(clienteSalvo));
    }

    @DeleteMapping("/clientes/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        clienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

