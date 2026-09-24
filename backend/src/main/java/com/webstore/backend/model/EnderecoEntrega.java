package com.webstore.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Snapshot imutável da compra, sem relação com o perfil do cliente. */
@Embeddable
public record EnderecoEntrega(
        @Column(name = "entrega_destinatario", length = 120, updatable = false) String destinatario,
        @Column(name = "entrega_cep", length = 8, updatable = false) String cep,
        @Column(name = "entrega_rua", length = 160, updatable = false) String rua,
        @Column(name = "entrega_numero", length = 20, updatable = false) String numero,
        @Column(name = "entrega_complemento", length = 120, updatable = false) String complemento,
        @Column(name = "entrega_bairro", length = 100, updatable = false) String bairro,
        @Column(name = "entrega_cidade", length = 100, updatable = false) String cidade,
        @Column(name = "entrega_uf", length = 2, updatable = false) String uf) {}
