package com.webstore.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "confirmacoes_email", indexes = {
        @Index(name = "ix_confirmacao_email_usuario", columnList = "usuario_id"),
        @Index(name = "ix_confirmacao_email_expira", columnList = "expira_em")
})
public class ConfirmacaoEmailToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "utilizado_em")
    private Instant utilizadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    protected ConfirmacaoEmailToken() { }

    public ConfirmacaoEmailToken(Usuario usuario, String tokenHash, Instant criadoEm, Instant expiraEm) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public Usuario getUsuario() { return usuario; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCriadoEm() { return criadoEm; }
    public boolean disponivelEm(Instant agora) { return utilizadoEm == null && revogadoEm == null && expiraEm.isAfter(agora); }
    public void utilizar(Instant agora) { utilizadoEm = agora; }
    public void revogar(Instant agora) { revogadoEm = agora; }
}
