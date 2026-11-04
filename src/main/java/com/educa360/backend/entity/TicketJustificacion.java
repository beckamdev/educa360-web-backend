package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ticket con el que un apoderado justifica una inasistencia de su hijo.
 *
 * No representa una falta nueva: es la solicitud de justificación que
 * Secretaría aprueba o rechaza. Al aprobarse, las asistencias FALTA del
 * alcance pasan a JUSTIFICADA y quedan vinculadas al ticket.
 *
 * REGLAS (en JustificacionService):
 * - sólo el apoderado con vínculo activo crea tickets de su representado;
 * - la fecha de inasistencia no puede ser futura;
 * - si se indica bloque, debe ser de un curso de la sección de la matrícula
 *   del alumno y la fecha debe caer en el día de ese bloque;
 * - sin bloque, el ticket cubre todo el día;
 * - no puede haber dos tickets activos que se solapen para el mismo alumno
 *   y fecha: el alcance de día completo prevalece;
 * - sólo se resuelve desde PENDIENTE y no hay vuelta atrás;
 * - no se elimina: los estados son la historia.
 */
@Entity
@Table(name = "tickets_justificacion")
public class TicketJustificacion extends Auditable {

    public enum Estado {
        PENDIENTE,
        APROBADO,
        RECHAZADO
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "apoderado_id", nullable = false)
    private Apoderado apoderado;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @Column(nullable = false)
    private LocalDate fechaInasistencia;

    /** NULL = el ticket cubre todos los bloques de ese día. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bloque_id")
    private HorarioBloque bloque;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(length = 500)
    private String urlArchivoSustento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    private LocalDateTime fechaResolucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por_id")
    private Secretaria resueltoPor;

    @Column(length = 500)
    private String observacionesAdmin;

    // Constructors

    public TicketJustificacion() {
        this.estado = Estado.PENDIENTE;
    }

    public TicketJustificacion(Apoderado apoderado, Estudiante estudiante,
                               LocalDate fechaInasistencia, HorarioBloque bloque,
                               String motivo, String urlArchivoSustento) {
        this();
        this.apoderado = apoderado;
        this.estudiante = estudiante;
        this.fechaInasistencia = fechaInasistencia;
        this.bloque = bloque;
        this.motivo = motivo;
        this.urlArchivoSustento = urlArchivoSustento;
    }

    // Helpers

    public boolean estaPendiente() {
        return estado == Estado.PENDIENTE;
    }

    public boolean estaAprobado() {
        return estado == Estado.APROBADO;
    }

    /** ¿El ticket cubre el bloque indicado? Sin bloque, cubre todo el día. */
    public boolean cubreBloque(Long bloqueId) {
        return bloque == null || (bloqueId != null && bloque.getId().equals(bloqueId));
    }

    // Getters and Setters

    public Apoderado getApoderado() {
        return apoderado;
    }

    public void setApoderado(Apoderado apoderado) {
        this.apoderado = apoderado;
    }

    public Estudiante getEstudiante() {
        return estudiante;
    }

    public void setEstudiante(Estudiante estudiante) {
        this.estudiante = estudiante;
    }

    public LocalDate getFechaInasistencia() {
        return fechaInasistencia;
    }

    public void setFechaInasistencia(LocalDate fechaInasistencia) {
        this.fechaInasistencia = fechaInasistencia;
    }

    public HorarioBloque getBloque() {
        return bloque;
    }

    public void setBloque(HorarioBloque bloque) {
        this.bloque = bloque;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getUrlArchivoSustento() {
        return urlArchivoSustento;
    }

    public void setUrlArchivoSustento(String urlArchivoSustento) {
        this.urlArchivoSustento = urlArchivoSustento;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(LocalDateTime fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }

    public Secretaria getResueltoPor() {
        return resueltoPor;
    }

    public void setResueltoPor(Secretaria resueltoPor) {
        this.resueltoPor = resueltoPor;
    }

    public String getObservacionesAdmin() {
        return observacionesAdmin;
    }

    public void setObservacionesAdmin(String observacionesAdmin) {
        this.observacionesAdmin = observacionesAdmin;
    }

    // toString seguro: sólo ids y campos simples

    @Override
    public String toString() {
        return "TicketJustificacion{" +
                "id=" + getId() +
                ", estudianteId=" + (estudiante != null ? estudiante.getId() : null) +
                ", fechaInasistencia=" + fechaInasistencia +
                ", bloqueId=" + (bloque != null ? bloque.getId() : null) +
                ", estado=" + estado +
                '}';
    }
}
