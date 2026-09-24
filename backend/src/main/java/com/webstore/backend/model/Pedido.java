package com.webstore.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@org.hibernate.annotations.Check(name = "ck_pedido_recebimento", constraints = Pedido.RECEBIMENTO_CONSTRAINT)
@Table(name = "pedidos", indexes = @jakarta.persistence.Index(name = "ix_pedido_reserva_expira", columnList = "reserva_status,reserva_expira_em"))
public class Pedido {
    public static final String RECEBIMENTO_CONSTRAINT = "modalidade IS NULL OR "
            + "(modalidade = 'RETIRADA' AND entrega_destinatario IS NULL AND entrega_cep IS NULL "
            + "AND entrega_rua IS NULL AND entrega_numero IS NULL AND entrega_complemento IS NULL "
            + "AND entrega_bairro IS NULL AND entrega_cidade IS NULL AND entrega_uf IS NULL) OR "
            + "(modalidade = 'ENTREGA' AND entrega_destinatario IS NOT NULL AND trim(entrega_destinatario) <> '' "
            + "AND entrega_cep IS NOT NULL AND length(entrega_cep) = 8 "
            + "AND entrega_rua IS NOT NULL AND trim(entrega_rua) <> '' "
            + "AND entrega_numero IS NOT NULL AND trim(entrega_numero) <> '' "
            + "AND entrega_bairro IS NOT NULL AND trim(entrega_bairro) <> '' "
            + "AND entrega_cidade IS NOT NULL AND trim(entrega_cidade) <> '' "
            + "AND entrega_uf IS NOT NULL AND length(entrega_uf) = 2)";
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PedidoStatus status = PedidoStatus.AGUARDANDO_PAGAMENTO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // SHA-256 do cliente e snapshot do carrinho; nulo para pedidos anteriores.
    @Column(name = "checkout_fingerprint", unique = true, length = 64)
    private String checkoutFingerprint;

    // Nulo somente para pedidos anteriores à implantação; nunca inferir destino antigo.
    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private ModalidadeRecebimento modalidade;
    @jakarta.persistence.Embedded
    private EnderecoEntrega enderecoEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "reserva_status", length = 20)
    private ReservaStatus reservaStatus;
    @Column(name = "reserva_expira_em")
    private java.time.Instant reservaExpiraEm;

    @Column(name = "mercado_pago_preference_id", unique = true)
    private String mercadoPagoPreferenceId;

    @Column(name = "mercado_pago_payment_id", unique = true)
    private String mercadoPagoPaymentId;

    @Column(name = "mercado_pago_status")
    private String mercadoPagoStatus;

    @Column(nullable = false)
    private boolean estoqueBaixado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tentativa_concluida_id", unique = true)
    private TentativaPagamento tentativaConcluida;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> itens = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long versao;

    @PrePersist
    void aoCriar() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public ModalidadeRecebimento getModalidade() { return modalidade; }
    public EnderecoEntrega getEnderecoEntrega() { return enderecoEntrega; }
    public void definirRecebimento(ModalidadeRecebimento modalidade, EnderecoEntrega endereco) {
        if (id != null) throw new IllegalStateException("O endereço do pedido não pode ser alterado.");
        this.modalidade = modalidade;
        this.enderecoEntrega = endereco;
    }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public PedidoStatus getStatus() { return status; }
    public void setStatus(PedidoStatus status) { this.status = status; }
    public BigDecimal getTotal() { return total; }
    public ReservaStatus getReservaStatus() { return reservaStatus; }
    public void setReservaStatus(ReservaStatus value) { reservaStatus = value; }
    public java.time.Instant getReservaExpiraEm() { return reservaExpiraEm; }
    public void setReservaExpiraEm(java.time.Instant value) { reservaExpiraEm = value; }
    public String getCheckoutFingerprint() { return checkoutFingerprint; }
    public void setCheckoutFingerprint(String value) { checkoutFingerprint = value; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getMercadoPagoPreferenceId() { return mercadoPagoPreferenceId; }
    public void setMercadoPagoPreferenceId(String value) { this.mercadoPagoPreferenceId = value; }
    public String getMercadoPagoPaymentId() { return mercadoPagoPaymentId; }
    public void setMercadoPagoPaymentId(String value) { this.mercadoPagoPaymentId = value; }
    public String getMercadoPagoStatus() { return mercadoPagoStatus; }
    public void setMercadoPagoStatus(String value) { this.mercadoPagoStatus = value; }
    public boolean isEstoqueBaixado() { return estoqueBaixado; }
    public TentativaPagamento getTentativaConcluida() { return tentativaConcluida; }
    public void setTentativaConcluida(TentativaPagamento value) { tentativaConcluida = value; }
    public void setEstoqueBaixado(boolean estoqueBaixado) { this.estoqueBaixado = estoqueBaixado; }
    public List<ItemPedido> getItens() { return itens; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
