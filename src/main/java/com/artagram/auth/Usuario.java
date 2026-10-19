package com.artagram.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "usuarios")
@EntityListeners(AuditingEntityListener.class)
public class Usuario {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String correo;

    @Column(name = "nombre_artista", nullable = false, unique = true)
    private String nombreArtista;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean verificado = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_global", nullable = false)
    private RolGlobal rolGlobal = RolGlobal.USUARIO;

    @Column(nullable = false)
    private boolean premium = false;

    @CreatedDate
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    @LastModifiedDate
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected Usuario() {
    }

    public Usuario(String correo, String nombreArtista, String passwordHash) {
        this.id = UUID.randomUUID();
        this.correo = correo;
        this.nombreArtista = nombreArtista;
        this.passwordHash = passwordHash;
    }

    public Usuario(UUID id, String correo, String nombreArtista, String passwordHash) {
        this.id = id != null ? id : UUID.randomUUID();
        this.correo = correo;
        this.nombreArtista = nombreArtista;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void ensureIdAndTimestamps() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.creadoEn == null) {
            this.creadoEn = now;
        }
        if (this.actualizadoEn == null) {
            this.actualizadoEn = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCorreo() {
        return correo;
    }

    public String getNombreArtista() {
        return nombreArtista;
    }

    public void setNombreArtista(String nombreArtista) {
        this.nombreArtista = nombreArtista;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isVerificado() {
        return verificado;
    }

    public void marcarVerificado() {
        this.verificado = true;
    }

    public RolGlobal getRolGlobal() {
        return rolGlobal;
    }

    public void setRolGlobal(RolGlobal rolGlobal) {
        this.rolGlobal = rolGlobal;
    }

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
