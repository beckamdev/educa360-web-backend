package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Asistencia de un alumno a un bloque de horario en una fecha concreta.
 *
 * Cuelga de la {@link Matricula} (no del estudiante suelto): así queda atada
 * al año y a la sección correctos. El bloque es la sesión semanal recurrente
 * del curso; no existe una entidad de "clase dictada".
 *
 * REGLAS (en AsistenciaService):
 * - única por (matrícula, bloque, fecha);
 * - el bloque debe pertenecer a un curso de la sección de la matrícula;
 * - la fecha debe caer en el día del bloque y dentro de un bimestre ABIERTO;
 * - sólo el docente del curso registra;
 * - el docente sólo marca "asistió / no asistió": TARDANZA la deriva el
 *   sistema según la hora de registro (15 minutos después del inicio);
 * - JUSTIFICADA sólo nace cuando Secretaría aprueba un ticket (Módulo 7);
 * - no se elimina: se corrige y queda la auditoría de {@link Auditable}.
 */
@Entity
@Table(name = "asistencias",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"matricula_id", "bloque_id", "fecha"}))
public class Asistencia extends Auditable {

    public enum Estado {
        PRESENTE,
        TARDANZA,
        FALTA,
        JUSTIFICADA
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bloque_id", nullable = false)
    private HorarioBloque bloque;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(length = 500)
    private String observacion;

    /**
     * Quién registró o corrigió por última vez. En M7, cuando Secretaría
     * justifica, la asistencia conserva el docente que la había registrado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Docente registradoPor;

    // Constructors

    public Asistencia() {
    }

    public Asistencia(Matricula matricula, HorarioBloque bloque, LocalDate fecha) {
        this();
        this.matricula = matricula;
        this.bloque = bloque;
        this.fecha = fecha;
    }

    // Helpers

    /** ¿Cuenta como asistencia efectiva para el 70%? */
    public boolean esEfectiva() {
        return estado == Estado.PRESENTE
                || estado == Estado.TARDANZA
                || estado == Estado.JUSTIFICADA;
    }

    public boolean esFalta() {
        return estado == Estado.FALTA;
    }

    public boolean estaJustificada() {
        return estado == Estado.JUSTIFICADA;
    }

    // Getters and Setters

    public Matricula getMatricula() {
        return matricula;
    }

    public void setMatricula(Matricula matricula) {
        this.matricula = matricula;
    }

    public HorarioBloque getBloque() {
        return bloque;
    }

    public void setBloque(HorarioBloque bloque) {
        this.bloque = bloque;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public Docente getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(Docente registradoPor) {
        this.registradoPor = registradoPor;
    }

    // toString seguro: sólo ids y campos simples

    @Override
    public String toString() {
        return "Asistencia{" +
                "id=" + getId() +
                ", matriculaId=" + (matricula != null ? matricula.getId() : null) +
                ", bloqueId=" + (bloque != null ? bloque.getId() : null) +
                ", fecha=" + fecha +
                ", estado=" + estado +
                '}';
    }
}
