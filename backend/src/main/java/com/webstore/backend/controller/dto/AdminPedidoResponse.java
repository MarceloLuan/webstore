package com.webstore.backend.controller.dto;

import com.webstore.backend.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

public record AdminPedidoResponse(Long id, LocalDateTime criadoEm, String clienteNome, String clienteEmail,
        String clienteTelefone, PedidoStatus statusPagamento, StatusEntrega statusEntrega,
        ModalidadeRecebimento modalidade, EnderecoEntrega enderecoEntrega,
        List<Item> itens, BigDecimal subtotalMercadorias, BigDecimal valorFrete,
        Integer prazoFreteDiasUteis, String prazoFrete, BigDecimal total,
        ReservaStatus reservaStatus, Instant reservaExpiraEm, String codigoRastreio) {
    public record Item(Long produtoTamanhoId, String produto, String tamanho, int quantidade, BigDecimal valorUnitario, BigDecimal subtotal) {}
}
