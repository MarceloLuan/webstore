package com.webstore.backend.service;

import com.webstore.backend.controller.dto.ProdutoRequest;
import com.webstore.backend.model.Produto;
import com.webstore.backend.repository.ProdutoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    public List<Produto> listarProdutosAtivos() {
        return produtoRepository.findAllByAtivoTrueOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public List<Produto> listarTodos() {
        return produtoRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
    }

    public Produto cadastrar(ProdutoRequest request) {
        validarRequest(request);

        Produto produto = new Produto();
        aplicarRequest(produto, request);
        return produtoRepository.save(produto);
    }

    public Produto atualizar(Long id, ProdutoRequest request) {
        validarRequest(request);

        Produto produto = buscarPorId(id);
        aplicarRequest(produto, request);
        return produtoRepository.save(produto);
    }

    public void excluir(Long id) {
        Produto produto = buscarPorId(id);
        produtoRepository.delete(produto);
    }

    private void aplicarRequest(Produto produto, ProdutoRequest request) {
        produto.setNome(normalizarTexto(request.getNome()));
        produto.setPreco(normalizarPreco(request.getPreco()));
        produto.setDestaque(StringUtils.hasText(request.getDestaque()) ? request.getDestaque().trim() : "Destaque");
        produto.setImagem(StringUtils.hasText(request.getImagem()) ? request.getImagem().trim() : null);
        produto.setDescricao(StringUtils.hasText(request.getDescricao()) ? request.getDescricao().trim() : null);
        produto.setAtivo(request.getAtivo() == null || request.getAtivo());
    }

    private void validarRequest(ProdutoRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do produto são obrigatórios.");
        }
        if (!StringUtils.hasText(request.getNome())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome é obrigatório.");
        }
        if (!StringUtils.hasText(request.getPreco())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço é obrigatório.");
        }
    }

    private String normalizarTexto(String value) {
        return value == null ? null : value.trim();
    }

    private BigDecimal normalizarPreco(String value) {
        String normalized = value.trim().replace(" ", "").replace("R$", "");

        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido.");
        }
    }
}

