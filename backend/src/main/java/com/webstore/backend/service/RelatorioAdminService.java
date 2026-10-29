package com.webstore.backend.service;

import com.webstore.backend.controller.dto.RelatorioAdminResponse;
import com.webstore.backend.model.Categoria;
import com.webstore.backend.model.ItemPedido;
import com.webstore.backend.model.Pedido;
import com.webstore.backend.model.PedidoStatus;
import com.webstore.backend.model.Produto;
import com.webstore.backend.model.ProdutoTamanho;
import com.webstore.backend.repository.PedidoRepository;
import com.webstore.backend.repository.ProdutoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RelatorioAdminService {
    private static final int LIMITE_DIAS = 366;

    private final PedidoRepository pedidos;
    private final ProdutoRepository produtos;

    public RelatorioAdminService(PedidoRepository pedidos, ProdutoRepository produtos) {
        this.pedidos = pedidos;
        this.produtos = produtos;
    }

    @Transactional(readOnly = true)
    public RelatorioAdminResponse gerar(LocalDate inicio, LocalDate fim, PedidoStatus status, Categoria categoria) {
        LocalDate dataFim = fim == null ? LocalDate.now() : fim;
        LocalDate dataInicio = inicio == null ? dataFim.minusDays(29) : inicio;
        validarPeriodo(dataInicio, dataFim);

        List<Pedido> pedidosFiltrados = status == null
                ? pedidos.findAllByCriadoEmGreaterThanEqualAndCriadoEmLessThanOrderByCriadoEmDesc(
                        dataInicio.atStartOfDay(), dataFim.plusDays(1).atStartOfDay())
                : pedidos.findAllByStatusAndCriadoEmGreaterThanEqualAndCriadoEmLessThanOrderByCriadoEmDesc(
                        status, dataInicio.atStartOfDay(), dataFim.plusDays(1).atStartOfDay());

        List<Produto> produtosFiltrados = produtos.findAllForReport().stream()
                .filter(produto -> categoria == null || produto.getCategoria() == categoria)
                .toList();

        return montarResposta(dataInicio, dataFim, status, categoria, pedidosFiltrados, produtosFiltrados);
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio.isAfter(fim)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data inicial deve ser anterior ou igual à data final.");
        }
        if (inicio.plusDays(LIMITE_DIAS - 1L).isBefore(fim)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O período máximo do relatório é de 366 dias.");
        }
    }

    private RelatorioAdminResponse montarResposta(LocalDate inicio, LocalDate fim, PedidoStatus status,
                                                    Categoria categoria, List<Pedido> listaPedidos,
                                                    List<Produto> listaProdutos) {
        BigDecimal valorPedidos = listaPedidos.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valorFrete = listaPedidos.stream().map(Pedido::getValorFrete)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        int quantidadeItens = listaPedidos.stream().flatMap(pedido -> pedido.getItens().stream())
                .mapToInt(ItemPedido::getQuantidade).sum();
        BigDecimal ticketMedio = listaPedidos.isEmpty() ? BigDecimal.ZERO
                : valorPedidos.divide(BigDecimal.valueOf(listaPedidos.size()), 2, RoundingMode.HALF_UP);

        Map<PedidoStatus, Acumulador> porStatus = new EnumMap<>(PedidoStatus.class);
        Map<Long, ProdutoAcumulado> porProduto = new LinkedHashMap<>();
        for (Pedido pedido : listaPedidos) {
            porStatus.computeIfAbsent(pedido.getStatus(), ignored -> new Acumulador()).adicionar(1, pedido.getTotal());
            for (ItemPedido item : pedido.getItens()) {
                Produto produto = item.getProdutoTamanho().getProduto();
                BigDecimal subtotal = item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));
                porProduto.computeIfAbsent(produto.getId(), ignored ->
                        new ProdutoAcumulado(produto.getId(), produto.getCodigo(), item.getNomeProduto()))
                        .adicionar(item.getQuantidade(), subtotal);
            }
        }

        List<RelatorioAdminResponse.PedidosPorStatus> statusPedidos = porStatus.entrySet().stream()
                .map(entry -> new RelatorioAdminResponse.PedidosPorStatus(
                        entry.getKey(), entry.getValue().quantidade, entry.getValue().valor))
                .sorted(Comparator.comparing(item -> item.status().name()))
                .toList();
        List<RelatorioAdminResponse.ProdutoVendido> vendidos = porProduto.values().stream()
                .sorted(Comparator.comparingInt(ProdutoAcumulado::quantidade).reversed()
                        .thenComparing(ProdutoAcumulado::nome))
                .map(item -> new RelatorioAdminResponse.ProdutoVendido(
                        item.id, item.codigo, item.nome, item.quantidade, item.valor))
                .toList();

        List<RelatorioAdminResponse.EstoqueProduto> estoque = new ArrayList<>();
        for (Produto produto : listaProdutos) {
            int total = produto.getTamanhos().stream().filter(this::variacaoAtiva)
                    .mapToInt(item -> item.getQuantidade() == null ? 0 : item.getQuantidade()).sum();
            int reservado = produto.getTamanhos().stream().filter(this::variacaoAtiva)
                    .mapToInt(ProdutoTamanho::getQuantidadeReservada).sum();
            estoque.add(new RelatorioAdminResponse.EstoqueProduto(produto.getId(), produto.getCodigo(), produto.getNome(),
                    produto.getCategoria(), Boolean.TRUE.equals(produto.getAtivo()), total, reservado, total - reservado));
        }
        estoque.sort(Comparator.comparingInt(RelatorioAdminResponse.EstoqueProduto::quantidadeDisponivel)
                .thenComparing(RelatorioAdminResponse.EstoqueProduto::nome));

        long ativos = listaProdutos.stream().filter(produto -> Boolean.TRUE.equals(produto.getAtivo())).count();
        long inativos = listaProdutos.size() - ativos;
        int disponiveis = estoque.stream().mapToInt(RelatorioAdminResponse.EstoqueProduto::quantidadeDisponivel).sum();
        int reservados = estoque.stream().mapToInt(RelatorioAdminResponse.EstoqueProduto::quantidadeReservada).sum();

        return new RelatorioAdminResponse(
                new RelatorioAdminResponse.Filtros(inicio, fim, status, categoria),
                new RelatorioAdminResponse.ResumoPedidos(listaPedidos.size(), quantidadeItens, valorPedidos, valorFrete, ticketMedio),
                statusPedidos,
                vendidos,
                new RelatorioAdminResponse.ResumoEstoque(ativos, inativos, disponiveis, reservados),
                estoque
        );
    }

    private boolean variacaoAtiva(ProdutoTamanho item) {
        return Boolean.TRUE.equals(item.getAtivo());
    }

    private static final class Acumulador {
        private long quantidade;
        private BigDecimal valor = BigDecimal.ZERO;
        private void adicionar(long quantidade, BigDecimal valor) {
            this.quantidade += quantidade;
            this.valor = this.valor.add(valor);
        }
    }

    private static final class ProdutoAcumulado {
        private final Long id;
        private final String codigo;
        private final String nome;
        private int quantidade;
        private BigDecimal valor = BigDecimal.ZERO;
        private ProdutoAcumulado(Long id, String codigo, String nome) {
            this.id = id; this.codigo = codigo; this.nome = nome;
        }
        private void adicionar(int quantidade, BigDecimal valor) {
            this.quantidade += quantidade;
            this.valor = this.valor.add(valor);
        }
        private int quantidade() { return quantidade; }
        private String nome() { return nome; }
    }
}
