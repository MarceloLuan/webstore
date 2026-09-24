package com.webstore.backend.service;

import com.webstore.backend.model.PedidoStatus;
import java.time.Instant;
import java.util.Set;
import static com.webstore.backend.model.PedidoStatus.*;

/** Transições por payment_id, nunca por ordem de chegada do webhook. */
public final class TransicoesPagamento {
    private TransicoesPagamento() {}

    public static PedidoStatus mapear(String status, String detalhe) {
        if (status == null) return null;
        return switch (status) {
            case "approved" -> "partially_refunded".equals(detalhe) ? REEMBOLSADO_PARCIAL : PAGO;
            case "pending", "in_process", "authorized" -> PENDENTE;
            case "in_mediation" -> EM_MEDIACAO;
            case "rejected" -> RECUSADO;
            case "cancelled" -> "expired".equals(detalhe) ? EXPIRADO : CANCELADO;
            case "refunded" -> REEMBOLSADO;
            case "charged_back" -> CHARGEBACK;
            default -> null;
        };
    }

    public static boolean permite(PedidoStatus atual, PedidoStatus novo, Instant anterior, Instant recebido) {
        if (novo == null) return false;
        if (anterior != null && recebido != null && recebido.isBefore(anterior)) return false;
        if (atual == novo) return true;
        if (anterior != null && recebido != null && recebido.equals(anterior)) return false;
        return switch (atual) {
            case AGUARDANDO_PAGAMENTO, ERRO, PENDENTE -> true;
            case PAGO, PAGO_EM_REVISAO -> Set.of(REEMBOLSADO_PARCIAL, REEMBOLSADO, CHARGEBACK, EM_MEDIACAO).contains(novo);
            case REEMBOLSADO_PARCIAL -> Set.of(REEMBOLSADO, CHARGEBACK, EM_MEDIACAO).contains(novo);
            case EM_MEDIACAO -> Set.of(REEMBOLSADO, CHARGEBACK).contains(novo)
                    || (Set.of(PAGO, REEMBOLSADO_PARCIAL).contains(novo)
                        && anterior != null && recebido != null && recebido.isAfter(anterior));
            case RECUSADO, CANCELADO, EXPIRADO, REEMBOLSADO, CHARGEBACK -> false;
        };
    }
}
