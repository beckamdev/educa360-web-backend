package com.educa360.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Token de push de un dispositivo físico vinculado a una cuenta.
 *
 * Un usuario puede tener varios (celular + tablet) y un dispositivo puede
 * cambiar de dueño: el token es único y, al iniciar sesión otra cuenta en
 * el mismo aparato, la fila se reasigna. El backend nunca genera tokens:
 * los obtiene la app móvil con {@code expo-notifications} y los registra
 * en {@code POST /api/v1/notificaciones/dispositivos}.
 *
 * El token es un secreto operativo: no se registra completo en logs.
 */
@Entity
@Table(name = "dispositivos")
public class Dispositivo extends Auditable {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlataformaDispositivo plataforma;

    // Constructors

    public Dispositivo() {
    }

    public Dispositivo(User usuario, String token, PlataformaDispositivo plataforma) {
        this.usuario = usuario;
        this.token = token;
        this.plataforma = plataforma;
    }

    // Getters and Setters

    public User getUsuario() {
        return usuario;
    }

    public void setUsuario(User usuario) {
        this.usuario = usuario;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public PlataformaDispositivo getPlataforma() {
        return plataforma;
    }

    public void setPlataforma(PlataformaDispositivo plataforma) {
        this.plataforma = plataforma;
    }

    @Override
    public String toString() {
        return "Dispositivo{" +
                "id=" + getId() +
                ", usuarioId=" + (usuario != null ? usuario.getId() : null) +
                ", plataforma=" + plataforma +
                ", token=" + tokenEnmascarado() +
                '}';
    }

    private String tokenEnmascarado() {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }
}
