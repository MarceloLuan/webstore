package com.webstore.backend.service;

import com.webstore.backend.controller.dto.ProdutoRequest;
import com.webstore.backend.controller.dto.TamanhoRequest;
import com.webstore.backend.model.*;
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
    private final com.webstore.backend.repository.ProdutoTamanhoRepository produtoTamanhoRepository;

    public ProdutoService(ProdutoRepository produtoRepository, com.webstore.backend.repository.ProdutoTamanhoRepository produtoTamanhoRepository) {
        this.produtoRepository = produtoRepository;
        this.produtoTamanhoRepository = produtoTamanhoRepository;
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
        // categoria
        if (request.getCategoria() != null) {
            Categoria c = Categoria.fromLabel(request.getCategoria());
            produto.setCategoria(c);
        }

        // tamanhos - reconcile existing
        java.util.List<TamanhoRequest> tamanhoRequests = request.getTamanhos();
        if (tamanhoRequests != null) {
            // build map of existing by label
            java.util.Map<String, ProdutoTamanho> existing = new java.util.HashMap<>();
            for (ProdutoTamanho pt : produto.getTamanhos()) {
                if (pt.getTamanho() != null) {
                    existing.put(pt.getTamanho().getLabel(), pt);
                }
            }

            java.util.List<ProdutoTamanho> newList = new java.util.ArrayList<>();
            for (TamanhoRequest tr : tamanhoRequests) {
                if (tr == null || tr.getTamanho() == null) continue;
                Tamanho t = Tamanho.fromLabel(tr.getTamanho());
                if (t == null) continue;
                ProdutoTamanho pt = existing.remove(t.getLabel());
                if (pt == null) {
                    pt = new ProdutoTamanho();
                    pt.setProduto(produto);
                    pt.setTamanho(t);
                }
                pt.setQuantidade(tr.getQuantidade());
                pt.setAtivo(true);
                newList.add(pt);
            }

            // orphanRemoval will delete removed ones
            produto.getTamanhos().clear();
            produto.getTamanhos().addAll(newList);
        }
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

