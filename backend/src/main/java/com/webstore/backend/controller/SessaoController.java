package com.webstore.backend.controller;

import com.webstore.backend.service.ContaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logout")
public class SessaoController {
    private final ContaService contaService;

    public SessaoController(ContaService contaService) {
        this.contaService = contaService;
    }

    @PostMapping
    public ResponseEntity<Void> logout() {
        contaService.encerrarSessoes();
        return ResponseEntity.noContent().build();
    }
}
