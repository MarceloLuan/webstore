package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.AdicionarItemCarrinhoRequest;
import com.webstore.backend.controller.dto.AtualizarItemCarrinhoRequest;
import com.webstore.backend.controller.dto.CarrinhoResponse;
import com.webstore.backend.service.CarrinhoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carrinho")
@PreAuthorize("hasRole('CLIENTE')")
public class CarrinhoController {

    private final CarrinhoService carrinhoService;

    public CarrinhoController(CarrinhoService carrinhoService) {
        this.carrinhoService = carrinhoService;
    }

    @GetMapping
    public ResponseEntity<CarrinhoResponse> buscar() {
        return ResponseEntity.ok(carrinhoService.buscar());
    }

    @PostMapping("/itens")
    public ResponseEntity<CarrinhoResponse> adicionar(@RequestBody AdicionarItemCarrinhoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carrinhoService.adicionar(request));
    }

    @PutMapping("/itens/{itemId}")
    public ResponseEntity<CarrinhoResponse> atualizar(
            @PathVariable Long itemId,
            @RequestBody AtualizarItemCarrinhoRequest request
    ) {
        return ResponseEntity.ok(carrinhoService.atualizar(itemId, request));
    }

    @DeleteMapping("/itens/{itemId}")
    public ResponseEntity<CarrinhoResponse> remover(@PathVariable Long itemId) {
        return ResponseEntity.ok(carrinhoService.remover(itemId));
    }

    @DeleteMapping
    public ResponseEntity<CarrinhoResponse> limpar() {
        return ResponseEntity.ok(carrinhoService.limpar());
    }
}
