package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Aviso persistente dirigido a un usuario concreto.
 *
 * No es un {@link Comunicado}: el comunicado es el contenido que alguien
 * redacta y la notificación es el aviso de que ese contenido (u otro evento)
 * existe. Un comunicado puede generar muchas notificaciones, una por
 * destinatario.
 *
 * REGLAS (en NotificacionService):
 * - el destinatario sale del token, nunca del request;
 * - se guarda en la misma transacción del evento y se empuja después del commit;
 * - no se elimina: el historial es la propia tabla;
 * - marcar como leída es idempotente.
 */
@Entity
@Table(name = "notificaciones")
public class Notificacion extends Auditable {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String mensaje;

    /** Id del comunicado, ticket, matrícula, asistencia, material o año. */
    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(nullable = false)
    private boolean leida = false;

    @Column(name = "leida_at")
    private LocalDateTime leidaAt;

    // Constructors

    public Notificacion() {
        this.leida = false;
    }

    public Notificacion(User usuario, TipoNotificacion tipo, String titulo,
                        String mensaje, Long referenciaId) {
        this();
        this.usuario = usuario;
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.referenciaId = referenciaId;
    }

    // Helpers

    public void marcarLeida() {
        if (!leida) {
            this.leida = true;
            this.leidaAt = LocalDateTime.now();
        }
    }

    // Getters and Setters

    public User getUsuario() {
        return usuario;
    }

    public void setUsuario(User usuario) {
        this.usuario = usuario;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public void setTipo(TipoNotificacion tipo) {
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Long getReferenciaId() {
        return referenciaId;
    }

    public void setReferenciaId(Long referenciaId) {
        this.referenciaId = referenciaId;
    }

    public boolean isLeida() {
        return leida;
    }

    public void setLeida(boolean leida) {
        this.leida = leida;
    }

    public LocalDateTime getLeidaAt() {
        return leidaAt;
    }

    public void setLeidaAt(LocalDateTime leidaAt) {
        this.leidaAt = leidaAt;
    }

    @Override
    public String toString() {
        return "Notificacion{" +
                "id=" + getId() +
                ", usuarioId=" + (usuario != null ? usuario.getId() : null) +
                ", tipo=" + tipo +
                ", leida=" + leida +
                '}';
    }
}
