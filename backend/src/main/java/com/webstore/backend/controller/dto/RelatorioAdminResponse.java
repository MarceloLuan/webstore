package com.webstore.backend.controller.dto;

import com.webstore.backend.model.Categoria;
import com.webstore.backend.model.PedidoStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioAdminResponse(
        Filtros filtros,
        ResumoPedidos resumoPedidos,
        List<PedidosPorStatus> pedidosPorStatus,
        List<ProdutoVendido> produtosVendidos,
        ResumoEstoque resumoEstoque,
        List<EstoqueProduto> estoqueProdutos
) {
    public record Filtros(LocalDate inicio, LocalDate fim, PedidoStatus status, Categoria categoria) {}

    public record ResumoPedidos(
            long quantidadePedidos,
            int quantidadeItens,
            BigDecimal valorPedidos,
            BigDecimal valorFrete,
            BigDecimal ticketMedio
    ) {}

    public record PedidosPorStatus(PedidoStatus status, long quantidade, BigDecimal valor) {}

    public record ProdutoVendido(
            Long produtoId,
            String codigo,
            String nome,
            int quantidade,
            BigDecimal valor
    ) {}

    public record ResumoEstoque(
            long produtosAtivos,
            long produtosInativos,
            int unidadesDisponiveis,
            int unidadesReservadas
    ) {}

    public record EstoqueProduto(
            Long produtoId,
            String codigo,
            String nome,
            Categoria categoria,
            boolean ativo,
            int quantidadeTotal,
            int quantidadeReservada,
            int quantidadeDisponivel
    ) {}
}
