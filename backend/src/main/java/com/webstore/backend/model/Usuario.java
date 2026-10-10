package com.webstore.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
        @UniqueConstraint(name = "uk_usuarios_email", columnNames = "email")
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING)
public abstract class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Column(nullable = false, length = 120)
    protected String nome;

    @Column(nullable = false, unique = true, length = 254)
    protected String email;

    @Column(length = 20)
    protected String telefone;

    @Column(nullable = false, length = 60)
    protected String senha;

    @Column(nullable = false)
    protected Boolean ativo = true;

    @Column(name = "email_confirmado", nullable = false, columnDefinition = "boolean default true")
    protected Boolean emailConfirmado = true;

    @Column(name = "email_confirmado_em")
    protected Instant emailConfirmadoEm;

    @Column(name = "sessoes_invalidas_antes_de")
    protected Instant sessoesInvalidasAntesDe;

    @Column(name = "versao_sessao", nullable = false, columnDefinition = "bigint default 0")
    protected Long versaoSessao = 0L;

    public Usuario() {
    }

    public Usuario(String nome, String email, String telefone, String senha) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.senha = senha;
        this.ativo = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public Boolean getEmailConfirmado() { return emailConfirmado; }
    public Instant getEmailConfirmadoEm() { return emailConfirmadoEm; }
    public void aguardarConfirmacaoEmail() {
        this.emailConfirmado = false;
        this.emailConfirmadoEm = null;
    }
    public void confirmarEmail() {
        this.emailConfirmado = true;
        this.emailConfirmadoEm = Instant.now();
    }
    public Instant getSessoesInvalidasAntesDe() { return sessoesInvalidasAntesDe; }
    public Long getVersaoSessao() { return versaoSessao == null ? 0L : versaoSessao; }
    public void invalidarSessoes(Instant instante) {
        this.sessoesInvalidasAntesDe = instante;
        this.versaoSessao = getVersaoSessao() + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Usuario usuario = (Usuario) o;
        return Objects.equals(id, usuario.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", email='" + email + '\'' +
                ", ativo=" + ativo +
                '}';
    }
}

