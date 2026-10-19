package com.artagram.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tokens_verificacion")
public class TokenVerificacion {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected TokenVerificacion() {
    }

    public TokenVerificacion(Usuario usuario, String token, Instant expiraEn) {
        this.usuario = usuario;
        this.token = token;
        this.expiraEn = expiraEn;
    }

    public UUID getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public boolean estaExpirado() {
        return Instant.now().isAfter(expiraEn);
    }

    public boolean estaUsado() {
        return usadoEn != null;
    }

    public void marcarUsado() {
        this.usadoEn = Instant.now();
    }
}
