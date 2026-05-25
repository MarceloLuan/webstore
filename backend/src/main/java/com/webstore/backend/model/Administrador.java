package com.webstore.backend.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ADMIN")
public class Administrador extends Usuario {

    public Administrador() {
        super();
    }

    public Administrador(String nome, String email, String telefone, String senha) {
        super(nome, email, telefone, senha);
    }
}

