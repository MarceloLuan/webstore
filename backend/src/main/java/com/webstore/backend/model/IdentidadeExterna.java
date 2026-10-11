package com.webstore.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "identidades_externas", uniqueConstraints =
        @UniqueConstraint(name = "uk_identidade_provedor_subject", columnNames = {"provedor", "subject"}))
public class IdentidadeExterna {
    public enum Provedor { GOOGLE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Provedor provedor;
    @Column(nullable = false, length = 255)
    private String subject;
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    protected IdentidadeExterna() { }
    public IdentidadeExterna(Usuario usuario, Provedor provedor, String subject) {
        this.usuario = usuario;
        this.provedor = provedor;
        this.subject = subject;
        this.criadoEm = Instant.now();
    }
    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public String getSubject() { return subject; }
    public Provedor getProvedor() { return provedor; }
}
