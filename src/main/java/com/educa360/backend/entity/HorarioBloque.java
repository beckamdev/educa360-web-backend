package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalTime;

/**
 * Sesión semanal recurrente de un curso: "Matemáticas · 3°A, lunes 08:00–09:00".
 *
 * Un curso se ve VARIAS veces por semana, por eso el horario es 1:N respecto
 * al Curso y no un par de campos dentro de él. Cada fila es una sesión que se
 * repite cada semana: la asistencia cuelga de (bloque, fecha).
 *
 * REGLAS (en CursoService.reemplazarHorario → 409):
 * - sin solapes con otro curso de la MISMA sección el mismo día
 *   (3°A no puede estar en dos sitios a la vez);
 * - sin solapes con otro curso del MISMO docente el mismo día.
 */
@Entity
@Table(name = "horario_bloques",
        uniqueConstraints = @UniqueConstraint(columnNames = {"curso_id", "dia", "hora_inicio"}))
public class HorarioBloque extends Auditable {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiaSemana dia;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    // Constructors

    public HorarioBloque() {
    }

    public HorarioBloque(Curso curso, DiaSemana dia, LocalTime horaInicio, LocalTime horaFin) {
        this();
        this.curso = curso;
        this.dia = dia;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    // Helpers

    /**
     * ¿Se solapa con otro bloque el mismo día? Intervalos [inicio, fin) abiertos
     * a la derecha: un bloque que termina a las 09:00 no choca con uno que empieza
     * a las 09:00 (turnos consecutivos, como en el patio).
     */
    public boolean seSolapaCon(HorarioBloque otro) {
        return this.dia == otro.dia
                && this.horaInicio.isBefore(otro.horaFin)
                && otro.horaInicio.isBefore(this.horaFin);
    }

    // Getters and Setters

    public Curso getCurso() {
        return curso;
    }

    public void setCurso(Curso curso) {
        this.curso = curso;
    }

    public DiaSemana getDia() {
        return dia;
    }

    public void setDia(DiaSemana dia) {
        this.dia = dia;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    // toString seguro para logs: sin la entidad Curso

    @Override
    public String toString() {
        return "HorarioBloque{" +
                "id=" + getId() +
                ", cursoId=" + (curso != null ? curso.getId() : null) +
                ", dia=" + dia +
                ", horaInicio=" + horaInicio +
                ", horaFin=" + horaFin +
                '}';
    }
}
