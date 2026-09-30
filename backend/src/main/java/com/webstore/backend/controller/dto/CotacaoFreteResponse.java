package com.webstore.backend.controller.dto;
import com.webstore.backend.model.CotacaoFrete;
import com.webstore.backend.model.ModalidadeRecebimento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record CotacaoFreteResponse(UUID id, ModalidadeRecebimento modalidade, BigDecimal mercadorias,
        BigDecimal valorFrete, Integer prazoDiasUteis, String prazo, BigDecimal total, Instant expiraEm) {
    public static CotacaoFreteResponse from(CotacaoFrete c) {
        return new CotacaoFreteResponse(c.getId(), c.getModalidade(), c.getMercadorias(), c.getValor(), c.getDiasUteis(), c.getPrazo(), c.getTotal(), c.getExpiraEm());
    }
}
