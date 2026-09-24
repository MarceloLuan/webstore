package com.webstore.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tentativas_pagamento", indexes = @Index(name = "ix_tentativa_pedido", columnList = "pedido_id"))
public class TentativaPagamento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;
    private String preferenceId;
    @Column(unique = true)
    private String paymentId;
    private String statusProvedor;
    private String detalheStatusProvedor;
    private java.time.Instant atualizadoProvedorEm;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PedidoStatus status = PedidoStatus.AGUARDANDO_PAGAMENTO;
    @Column(length = 2048)
    private String checkoutUrl;
    private boolean aprovacaoDuplicada;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;
    @PrePersist void criar() { criadoEm = LocalDateTime.now(); atualizadoEm = criadoEm; }
    @PreUpdate void atualizar() { atualizadoEm = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido value) { pedido = value; }
    public String getPreferenceId() { return preferenceId; }
    public void setPreferenceId(String value) { preferenceId = value; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }
    public String getStatusProvedor() { return statusProvedor; }
    public void setStatusProvedor(String value) { statusProvedor = value; }
    public String getDetalheStatusProvedor() { return detalheStatusProvedor; }
    public void setDetalheStatusProvedor(String value) { detalheStatusProvedor = value; }
    public java.time.Instant getAtualizadoProvedorEm() { return atualizadoProvedorEm; }
    public void setAtualizadoProvedorEm(java.time.Instant value) { atualizadoProvedorEm = value; }
    public PedidoStatus getStatus() { return status; }
    public void setStatus(PedidoStatus value) { status = value; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String value) { checkoutUrl = value; }
    public boolean isAprovacaoDuplicada() { return aprovacaoDuplicada; }
    public void setAprovacaoDuplicada(boolean value) { aprovacaoDuplicada = value; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
