package com.webstore.backend.model;

public enum PedidoStatus {
    AGUARDANDO_PAGAMENTO,
    PAGO,
    PAGO_EM_REVISAO,
    PENDENTE,
    RECUSADO,
    CANCELADO,
    EXPIRADO,
    REEMBOLSADO,
    REEMBOLSADO_PARCIAL,
    CHARGEBACK,
    EM_MEDIACAO,
    ERRO
}
