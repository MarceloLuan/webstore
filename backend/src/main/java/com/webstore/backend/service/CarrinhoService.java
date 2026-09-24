package com.webstore.backend.service;

import com.webstore.backend.controller.dto.AdicionarItemCarrinhoRequest;
import com.webstore.backend.controller.dto.AtualizarItemCarrinhoRequest;
import com.webstore.backend.controller.dto.CarrinhoResponse;
import com.webstore.backend.controller.dto.ItemCarrinhoResponse;
import com.webstore.backend.model.Carrinho;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.model.ItemCarrinho;
import com.webstore.backend.model.Produto;
import com.webstore.backend.model.ProdutoTamanho;
import com.webstore.backend.repository.CarrinhoRepository;
import com.webstore.backend.repository.ClienteRepository;
import com.webstore.backend.repository.ProdutoTamanhoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class CarrinhoService {

    private final CarrinhoRepository carrinhoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoTamanhoRepository produtoTamanhoRepository;

    public CarrinhoService(
            CarrinhoRepository carrinhoRepository,
            ClienteRepository clienteRepository,
            ProdutoTamanhoRepository produtoTamanhoRepository
    ) {
        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoTamanhoRepository = produtoTamanhoRepository;
    }

    @Transactional(readOnly = true)
    public CarrinhoResponse buscar() {
        Cliente cliente = buscarClienteAutenticado();
        return carrinhoRepository.findByClienteId(cliente.getId())
                .map(this::toResponse)
                .orElseGet(this::carrinhoVazio);
    }

    public CarrinhoResponse adicionar(AdicionarItemCarrinhoRequest request) {
        if (request == null || request.getProdutoTamanhoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um tamanho do produto.");
        }

        int quantidade = validarQuantidadePositiva(request.getQuantidade());
        ProdutoTamanho variacao = buscarVariacaoDisponivel(request.getProdutoTamanhoId());
        Carrinho carrinho = buscarOuCriarCarrinho(buscarClienteAutenticado());

        ItemCarrinho item = carrinho.getItens().stream()
                .filter(atual -> atual.getProdutoTamanho().getId().equals(variacao.getId()))
                .findFirst()
                .orElseGet(() -> {
                    ItemCarrinho novo = new ItemCarrinho();
                    novo.setCarrinho(carrinho);
                    novo.setProdutoTamanho(variacao);
                    novo.setQuantidade(0);
                    carrinho.getItens().add(novo);
                    return novo;
                });

        int novaQuantidade = item.getQuantidade() + quantidade;
        validarEstoque(variacao, novaQuantidade);
        item.setQuantidade(novaQuantidade);

        return toResponse(carrinhoRepository.save(carrinho));
    }

    public CarrinhoResponse atualizar(Long itemId, AtualizarItemCarrinhoRequest request) {
        if (itemId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item do carrinho inválido.");
        }

        int quantidade = validarQuantidadePositiva(request == null ? null : request.getQuantidade());
        Carrinho carrinho = buscarCarrinhoExistente(buscarClienteAutenticado());
        ItemCarrinho item = buscarItem(carrinho, itemId);
        ProdutoTamanho variacao = buscarVariacaoDisponivel(item.getProdutoTamanho().getId());

        validarEstoque(variacao, quantidade);
        item.setQuantidade(quantidade);
        return toResponse(carrinhoRepository.save(carrinho));
    }

    public CarrinhoResponse remover(Long itemId) {
        Carrinho carrinho = buscarCarrinhoExistente(buscarClienteAutenticado());
        ItemCarrinho item = buscarItem(carrinho, itemId);
        carrinho.getItens().remove(item);
        return toResponse(carrinhoRepository.save(carrinho));
    }

    public CarrinhoResponse limpar() {
        Cliente cliente = buscarClienteAutenticado();
        return carrinhoRepository.findByClienteId(cliente.getId())
                .map(carrinho -> {
                    carrinho.getItens().clear();
                    return toResponse(carrinhoRepository.save(carrinho));
                })
                .orElseGet(this::carrinhoVazio);
    }

    private Carrinho buscarOuCriarCarrinho(Cliente cliente) {
        return carrinhoRepository.findByClienteId(cliente.getId())
                .orElseGet(() -> {
                    Carrinho carrinho = new Carrinho();
                    carrinho.setCliente(cliente);
                    return carrinho;
                });
    }

    private Carrinho buscarCarrinhoExistente(Cliente cliente) {
        return carrinhoRepository.findByClienteId(cliente.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrinho não encontrado."));
    }

    private ItemCarrinho buscarItem(Carrinho carrinho, Long itemId) {
        return carrinho.getItens().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado no carrinho."));
    }

    private ProdutoTamanho buscarVariacaoDisponivel(Long variacaoId) {
        ProdutoTamanho variacao = produtoTamanhoRepository.findById(variacaoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tamanho do produto não encontrado."));
        Produto produto = variacao.getProduto();

        if (Boolean.FALSE.equals(variacao.getAtivo()) || Boolean.FALSE.equals(produto.getAtivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este produto não está mais disponível.");
        }

        if (variacao.getQuantidadeDisponivel() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este tamanho está sem estoque.");
        }

        return variacao;
    }

    private int validarQuantidadePositiva(Integer quantidade) {
        if (quantidade == null || quantidade < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade deve ser de pelo menos 1.");
        }
        return quantidade;
    }

    private void validarEstoque(ProdutoTamanho variacao, int quantidade) {
        if (quantidade > variacao.getQuantidadeDisponivel()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Quantidade indisponível. Há " + variacao.getQuantidadeDisponivel() + " unidade(s) em estoque."
            );
        }
    }

    private Cliente buscarClienteAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cliente não autenticado.");
        }

        Cliente cliente = clienteRepository.findByEmail(authentication.getName().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Apenas clientes podem gerenciar o carrinho."
                ));

        if (Boolean.FALSE.equals(cliente.getAtivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cliente inativo.");
        }

        return cliente;
    }

    private CarrinhoResponse toResponse(Carrinho carrinho) {
        List<ItemCarrinhoResponse> itens = carrinho.getItens().stream()
                .sorted(Comparator.comparing(ItemCarrinho::getId))
                .map(this::toItemResponse)
                .toList();

        CarrinhoResponse response = new CarrinhoResponse();
        response.setId(carrinho.getId());
        response.setItens(itens);
        response.setQuantidadeTotal(itens.stream().mapToInt(ItemCarrinhoResponse::getQuantidade).sum());
        response.setSubtotal(itens.stream()
                .map(ItemCarrinhoResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return response;
    }

    private ItemCarrinhoResponse toItemResponse(ItemCarrinho item) {
        ProdutoTamanho variacao = item.getProdutoTamanho();
        Produto produto = variacao.getProduto();
        BigDecimal preco = variacao.getPreco() != null ? variacao.getPreco() : produto.getPreco();

        ItemCarrinhoResponse response = new ItemCarrinhoResponse();
        response.setId(item.getId());
        response.setProdutoId(produto.getId());
        response.setProdutoTamanhoId(variacao.getId());
        response.setCodigo(produto.getCodigo());
        response.setNome(produto.getNome());
        response.setImagem(produto.getImagem());
        response.setTamanho(variacao.getTamanho().getLabel());
        response.setQuantidade(item.getQuantidade());
        response.setEstoqueDisponivel(variacao.getQuantidadeDisponivel());
        response.setPrecoUnitario(preco);
        response.setSubtotal(preco.multiply(BigDecimal.valueOf(item.getQuantidade())));
        return response;
    }

    private CarrinhoResponse carrinhoVazio() {
        CarrinhoResponse response = new CarrinhoResponse();
        response.setQuantidadeTotal(0);
        response.setSubtotal(BigDecimal.ZERO);
        return response;
    }
}
