package com.webstore.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cotacoes_frete", indexes = @Index(name = "ix_cotacao_expiracao", columnList = "expira_em"))
public class CotacaoFrete {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private Long clienteId;
    @Column(nullable = false, length = 64, updatable = false) private String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private ModalidadeRecebimento modalidade;
    @Column(nullable = false, precision = 12, scale = 2, updatable = false) private BigDecimal mercadorias;
    @Column(nullable = false, precision = 12, scale = 2, updatable = false) private BigDecimal valor;
    @Column(updatable = false) private Integer diasUteis;
    @Column(nullable = false, length = 200, updatable = false) private String prazo;
    @Column(nullable = false, length = 60, updatable = false) private String origem;
    @Column(nullable = false, updatable = false) private Instant expiraEm;
    protected CotacaoFrete() {}
    public CotacaoFrete(Long clienteId, String fingerprint, ModalidadeRecebimento modalidade,
                        BigDecimal mercadorias, BigDecimal valor, Integer diasUteis, String prazo, String origem, Instant expiraEm) {
        this.id = UUID.randomUUID(); this.clienteId = clienteId; this.fingerprint = fingerprint; this.modalidade = modalidade;
        this.mercadorias = mercadorias; this.valor = valor; this.diasUteis = diasUteis; this.prazo = prazo; this.origem = origem; this.expiraEm = expiraEm;
    }
    public UUID getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getFingerprint() { return fingerprint; }
    public ModalidadeRecebimento getModalidade() { return modalidade; }
    public BigDecimal getMercadorias() { return mercadorias; }
    public BigDecimal getValor() { return valor; }
    public Integer getDiasUteis() { return diasUteis; }
    public String getPrazo() { return prazo; }
    public String getOrigem() { return origem; }
    public Instant getExpiraEm() { return expiraEm; }
    public BigDecimal getTotal() { return mercadorias.add(valor); }
}
