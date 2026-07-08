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
        validarCodigoUnico(request.getCodigo(), null);
        aplicarRequest(produto, request);
        return produtoRepository.save(produto);
    }

    public Produto atualizar(Long id, ProdutoRequest request) {
        validarRequest(request);

        Produto produto = buscarPorId(id);
        validarCodigoUnico(request.getCodigo(), produto.getId());
        aplicarRequest(produto, request);
        return produtoRepository.save(produto);
    }

    public void excluir(Long id) {
        Produto produto = buscarPorId(id);
        produtoRepository.delete(produto);
    }

    private void aplicarRequest(Produto produto, ProdutoRequest request) {
        produto.setCodigo(normalizarCodigo(request.getCodigo()));
        produto.setNome(normalizarTexto(request.getNome()));
        produto.setPreco(normalizarPreco(request.getPreco()));
        produto.setDestaque(StringUtils.hasText(request.getDestaque()) ? request.getDestaque().trim() : "Destaque");
        produto.setImagem(StringUtils.hasText(request.getImagem()) ? request.getImagem().trim() : null);
        produto.setDescricao(StringUtils.hasText(request.getDescricao()) ? request.getDescricao().trim() : null);
        produto.setAtivo(request.getAtivo() == null || request.getAtivo());
        produto.setCategoria(validarCategoria(request.getCategoria()));

        // tamanhos - reconcile existing
        java.util.List<TamanhoRequest> tamanhoRequests = request.getTamanhos();
        if (tamanhoRequests == null || tamanhoRequests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos um tamanho.");
        }

        // build map of existing by label
        java.util.Map<String, ProdutoTamanho> existing = new java.util.HashMap<>();
        for (ProdutoTamanho pt : produto.getTamanhos()) {
            if (pt.getTamanho() != null) {
                existing.put(pt.getTamanho().getLabel(), pt);
            }
        }

        java.util.List<ProdutoTamanho> newList = new java.util.ArrayList<>();
        for (TamanhoRequest tr : tamanhoRequests) {
            Tamanho tamanho = validarTamanho(tr == null ? null : tr.getTamanho());
            Integer quantidade = validarQuantidade(tr == null ? null : tr.getQuantidade());

            ProdutoTamanho pt = existing.remove(tamanho.getLabel());
            if (pt == null) {
                pt = new ProdutoTamanho();
                pt.setProduto(produto);
                pt.setTamanho(tamanho);
            }
            pt.setQuantidade(quantidade);
            pt.setAtivo(true);
            newList.add(pt);
        }

        // orphanRemoval will delete removed ones
        produto.getTamanhos().clear();
        produto.getTamanhos().addAll(newList);
    }

    private void validarRequest(ProdutoRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do produto são obrigatórios.");
        }
        if (!StringUtils.hasText(request.getNome())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome é obrigatório.");
        }
        if (!StringUtils.hasText(request.getCodigo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codigo do produto e obrigatorio.");
        }
        if (!StringUtils.hasText(request.getPreco())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço é obrigatório.");
        }
        if (!StringUtils.hasText(request.getCategoria())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria é obrigatória.");
        }
        if (request.getTamanhos() == null || request.getTamanhos().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos um tamanho.");
        }
    }

    private Categoria validarCategoria(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria é obrigatória.");
        }

        Categoria categoria = Categoria.fromLabel(value);
        if (categoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida.");
        }

        return categoria;
    }

    private Tamanho validarTamanho(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tamanho é obrigatório.");
        }

        Tamanho tamanho = Tamanho.fromLabel(value);
        if (tamanho == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tamanho inválido.");
        }

        return tamanho;
    }

    private Integer validarQuantidade(Integer value) {
        if (value == null || value < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade inválida.");
        }

        return value;
    }

    private String normalizarTexto(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizarCodigo(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private void validarCodigoUnico(String value, Long produtoIdAtual) {
        String codigo = normalizarCodigo(value);

        produtoRepository.findByCodigo(codigo)
                .filter(produto -> produtoIdAtual == null || !produto.getId().equals(produtoIdAtual))
                .ifPresent(produto -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe um produto cadastrado com este codigo.");
                });
    }

    private BigDecimal normalizarPreco(String value) {
        String normalized = value.trim().replace(" ", "").replace("R$", "");

        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }

        try {
            BigDecimal preco = new BigDecimal(normalized);
            if (preco.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O preço não pode ser negativo.");
            }
            return preco;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido.");
        }
    }
}

