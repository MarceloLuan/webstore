package com.webstore.backend.service;

import com.webstore.backend.controller.dto.*;
import com.webstore.backend.model.*;
import com.webstore.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class AdminPedidoService {
    private final PedidoRepository pedidos;
    public AdminPedidoService(PedidoRepository pedidos) { this.pedidos = pedidos; }

    @Transactional(readOnly = true)
    public List<AdminPedidoResponse> listar() {
        return pedidos.findAllByOrderByCriadoEmDesc().stream().map(this::mapear).toList();
    }

    // A consulta usa o lock do pedido para garantir um snapshot consistente
    // enquanto os itens e o cliente são materializados.
    @Transactional
    public AdminPedidoResponse buscar(Long id) {
        return pedidos.findWithItensForUpdateById(id).map(this::mapear)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    }

    @Transactional
    public AdminPedidoResponse atualizarStatus(Long id, AdminPedidoStatusRequest request) {
        Pedido pedido = pedidos.findWithItensForUpdateById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        StatusEntrega novo = request == null ? null : request.validar();
        String rastreio = request == null ? null : request.rastreioNormalizado();
        StatusEntrega atual = pedido.getStatusEntrega();
        if (!permitida(atual, novo) || (pedido.getModalidade() == ModalidadeRecebimento.RETIRADA && novo == StatusEntrega.ENVIADO)
                || (pedido.getModalidade() == ModalidadeRecebimento.ENTREGA && novo == StatusEntrega.PRONTO_PARA_RETIRADA)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Transição de entrega não permitida: " + atual + " para " + novo + ".");
        pedido.setStatusEntrega(novo);
        if (rastreio != null) pedido.setCodigoRastreio(rastreio);
        if (novo == StatusEntrega.ENVIADO && pedido.getModalidade() == ModalidadeRecebimento.ENTREGA && rastreio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o código de rastreio ao marcar o pedido como enviado.");
        }
        return mapear(pedido);
    }

    private boolean permitida(StatusEntrega atual, StatusEntrega novo) {
        if (atual == novo) return true;
        if (atual == StatusEntrega.ENTREGUE) return false;
        if (atual == StatusEntrega.CANCELADO) return false;
        if (novo == StatusEntrega.PRONTO_PARA_RETIRADA && atual != StatusEntrega.EM_SEPARACAO
                && atual != StatusEntrega.RECEBIDO) return false;
        if (novo == StatusEntrega.CANCELADO) return atual != StatusEntrega.ENTREGUE;
        return switch (atual) {
            case RECEBIDO -> novo == StatusEntrega.EM_SEPARACAO || novo == StatusEntrega.PRONTO_PARA_RETIRADA || novo == StatusEntrega.ENVIADO;
            case EM_SEPARACAO -> novo == StatusEntrega.PRONTO_PARA_RETIRADA || novo == StatusEntrega.ENVIADO;
            case PRONTO_PARA_RETIRADA -> novo == StatusEntrega.ENTREGUE;
            case ENVIADO -> novo == StatusEntrega.ENTREGUE;
            case ENTREGUE, CANCELADO -> false;
        };
    }


    private AdminPedidoResponse mapear(Pedido p) {
        List<AdminPedidoResponse.Item> itens = p.getItens().stream().map(i -> new AdminPedidoResponse.Item(
                i.getProdutoTamanho().getId(), i.getNomeProduto(), i.getTamanho(), i.getQuantidade(),
                i.getPrecoUnitario(), i.getPrecoUnitario().multiply(java.math.BigDecimal.valueOf(i.getQuantidade())))).toList();
        Cliente cliente = p.getCliente();
        return new AdminPedidoResponse(p.getId(), p.getCriadoEm(), cliente.getNome(), cliente.getEmail(), cliente.getTelefone(),
                p.getStatus(), p.getStatusEntrega(), p.getModalidade(), p.getEnderecoEntrega(), itens,
                p.getSubtotalMercadorias(), p.getValorFrete(), p.getPrazoFreteDiasUteis(), p.getPrazoFrete(), p.getTotal(),
                p.getReservaStatus(), p.getReservaExpiraEm(), p.getCodigoRastreio());
    }
}
