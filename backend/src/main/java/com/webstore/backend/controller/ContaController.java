package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.AtualizarContaRequest;
import com.webstore.backend.controller.dto.UsuarioResponse;
import com.webstore.backend.service.ContaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/minha-conta")
public class ContaController {

    private final ContaService contaService;

    public ContaController(ContaService contaService) {
        this.contaService = contaService;
    }

    @GetMapping
    public ResponseEntity<UsuarioResponse> buscar() {
        return ResponseEntity.ok(contaService.buscarConta());
    }

    @PutMapping
    public ResponseEntity<UsuarioResponse> atualizar(@RequestBody AtualizarContaRequest request) {
        return ResponseEntity.ok(contaService.atualizarConta(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> deletar() {
        contaService.deletarConta();
        return ResponseEntity.noContent().build();
    }
}
