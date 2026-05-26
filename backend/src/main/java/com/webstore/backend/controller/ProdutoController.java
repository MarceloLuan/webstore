package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.ProdutoRequest;
import com.webstore.backend.controller.dto.ProdutoResponse;
import com.webstore.backend.model.Produto;
import com.webstore.backend.service.ProdutoService;
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

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping("/produtos")
    public ResponseEntity<List<ProdutoResponse>> listarProdutosAtivos() {
        return ResponseEntity.ok(
                produtoService.listarProdutosAtivos().stream()
                        .map(ProdutoResponse::from)
                        .toList()
        );
    }

    @GetMapping("/admin/produtos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProdutoResponse>> listarTodos() {
        return ResponseEntity.ok(
                produtoService.listarTodos().stream()
                        .map(ProdutoResponse::from)
                        .toList()
        );
    }

    @PostMapping("/admin/produtos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProdutoResponse> criar(@RequestBody ProdutoRequest request) {
        Produto produto = produtoService.cadastrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProdutoResponse.from(produto));
    }

    @PutMapping("/admin/produtos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProdutoResponse> atualizar(@PathVariable Long id, @RequestBody ProdutoRequest request) {
        Produto produto = produtoService.atualizar(id, request);
        return ResponseEntity.ok(ProdutoResponse.from(produto));
    }

    @DeleteMapping("/admin/produtos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}

