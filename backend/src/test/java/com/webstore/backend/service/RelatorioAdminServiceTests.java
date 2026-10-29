package com.webstore.backend.service;

import com.webstore.backend.model.Categoria;
import com.webstore.backend.model.ItemPedido;
import com.webstore.backend.model.Pedido;
import com.webstore.backend.model.PedidoStatus;
import com.webstore.backend.model.Produto;
import com.webstore.backend.model.ProdutoTamanho;
import com.webstore.backend.model.Tamanho;
import com.webstore.backend.repository.PedidoRepository;
import com.webstore.backend.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioAdminServiceTests {
    @Mock PedidoRepository pedidos;
    @Mock ProdutoRepository produtos;

    @Test
    void agregaSomenteDadosPersistidosDosPedidosEEstoque() {
        LocalDate inicio = LocalDate.of(2026, 9, 1);
        LocalDate fim = LocalDate.of(2026, 9, 30);
        Produto produto = produto(10L, "VEST-10", "Vestido Floral", 8, 2);
        Pedido pedido = pedidoPago(produto, 2, "90.00", "12.00");

        when(pedidos.findAllByStatusAndCriadoEmGreaterThanEqualAndCriadoEmLessThanOrderByCriadoEmDesc(
                PedidoStatus.PAGO, inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay())).thenReturn(List.of(pedido));
        when(produtos.findAllForReport()).thenReturn(List.of(produto));

        var resposta = new RelatorioAdminService(pedidos, produtos)
                .gerar(inicio, fim, PedidoStatus.PAGO, Categoria.VESTIDO);

        assertThat(resposta.resumoPedidos().quantidadePedidos()).isEqualTo(1);
        assertThat(resposta.resumoPedidos().quantidadeItens()).isEqualTo(2);
        assertThat(resposta.resumoPedidos().valorPedidos()).isEqualByComparingTo("90.00");
        assertThat(resposta.resumoPedidos().valorFrete()).isEqualByComparingTo("12.00");
        assertThat(resposta.resumoPedidos().ticketMedio()).isEqualByComparingTo("90.00");
        assertThat(resposta.produtosVendidos()).singleElement().satisfies(item -> {
            assertThat(item.produtoId()).isEqualTo(10L);
            assertThat(item.quantidade()).isEqualTo(2);
            assertThat(item.valor()).isEqualByComparingTo("78.00");
        });
        assertThat(resposta.resumoEstoque().unidadesDisponiveis()).isEqualTo(6);
        assertThat(resposta.resumoEstoque().unidadesReservadas()).isEqualTo(2);
        verify(produtos).findAllForReport();
    }

    @Test
    void rejeitaPeriodoInvertidoOuMaiorQueUmAno() {
        RelatorioAdminService service = new RelatorioAdminService(pedidos, produtos);
        assertThatThrownBy(() -> service.gerar(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("data inicial");
        assertThatThrownBy(() -> service.gerar(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 2, 1), null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("366 dias");
    }

    private Produto produto(Long id, String codigo, String nome, int quantidade, int reservada) {
        Produto produto = new Produto();
        produto.setId(id); produto.setCodigo(codigo); produto.setNome(nome); produto.setCategoria(Categoria.VESTIDO);
        produto.setPreco(new BigDecimal("39.00")); produto.setAtivo(true); produto.setDestaque("Destaque");
        ProdutoTamanho tamanho = new ProdutoTamanho();
        tamanho.setProduto(produto); tamanho.setTamanho(Tamanho.M); tamanho.setQuantidade(quantidade);
        tamanho.setQuantidadeReservada(reservada); tamanho.setAtivo(true);
        produto.getTamanhos().add(tamanho);
        return produto;
    }

    private Pedido pedidoPago(Produto produto, int quantidade, String total, String frete) {
        Pedido pedido = new Pedido();
        pedido.setStatus(PedidoStatus.PAGO); pedido.setTotal(new BigDecimal(total));
        ReflectionTestUtils.setField(pedido, "valorFrete", new BigDecimal(frete));
        ReflectionTestUtils.setField(pedido, "criadoEm", LocalDateTime.of(2026, 9, 10, 12, 0));
        ItemPedido item = new ItemPedido();
        item.setPedido(pedido); item.setProdutoTamanho(produto.getTamanhos().get(0));
        item.setNomeProduto(produto.getNome()); item.setTamanho("M"); item.setQuantidade(quantidade);
        item.setPrecoUnitario(new BigDecimal("39.00"));
        pedido.getItens().add(item);
        return pedido;
    }
}
