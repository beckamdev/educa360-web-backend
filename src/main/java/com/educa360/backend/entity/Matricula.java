package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Matrícula: el registro académico ANUAL del estudiante en una sección.
 *
 * No representa una cuota ni un pago: en este sistema no hay pensiones.
 * Es la inscripción que da sección al alumno durante un año escolar. Sin
 * ella, "sus cursos", su horario y su boleta no tienen a qué colgarse.
 *
 * REGLAS (en MatriculaService):
 * - única por (estudiante, año): unique en la tabla;
 * - un estudiante NO puede tener dos matrículas en el MISMO año
 *   (ir de 3°A a 3°B es un cambio de sección, no una segunda inscripción);
 * - la sección debe pertenecer al mismo año;
 * - sólo estudiantes ACTIVOS se matriculan.
 *
 * Nace EN_CURSO. RETIRADO lo asigna el retiro; APROBADO/DESAPROBADO los
 * asigna el cierre del año (Módulo 10).
 */
@Entity
@Table(name = "matriculas",
        uniqueConstraints = @UniqueConstraint(columnNames = {"estudiante_id", "anio_id"}))
public class Matricula extends Auditable {

    public enum SituacionFinal {
        EN_CURSO,
        APROBADO,
        DESAPROBADO,
        RETIRADO
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "anio_id", nullable = false)
    private AnioEscolar anio;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "seccion_id", nullable = false)
    private Seccion seccion;

    @Column(nullable = false)
    private LocalDate fechaMatricula;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacionFinal situacionFinal;

    @Column(length = 500)
    private String observacion;

    // Constructors

    public Matricula() {
        this.situacionFinal = SituacionFinal.EN_CURSO;
    }

    public Matricula(Estudiante estudiante, AnioEscolar anio, Seccion seccion,
                     LocalDate fechaMatricula) {
        this();
        this.estudiante = estudiante;
        this.anio = anio;
        this.seccion = seccion;
        this.fechaMatricula = fechaMatricula;
    }

    // Helpers

    public boolean estaRetirada() {
        return situacionFinal == SituacionFinal.RETIRADO;
    }

    public boolean estaEnCurso() {
        return situacionFinal == SituacionFinal.EN_CURSO;
    }

    // Getters and Setters

    public Estudiante getEstudiante() {
        return estudiante;
    }

    public void setEstudiante(Estudiante estudiante) {
        this.estudiante = estudiante;
    }

    public AnioEscolar getAnio() {
        return anio;
    }

    public void setAnio(AnioEscolar anio) {
        this.anio = anio;
    }

    public Seccion getSeccion() {
        return seccion;
    }

    public void setSeccion(Seccion seccion) {
        this.seccion = seccion;
    }

    public LocalDate getFechaMatricula() {
        return fechaMatricula;
    }

    public void setFechaMatricula(LocalDate fechaMatricula) {
        this.fechaMatricula = fechaMatricula;
    }

    public SituacionFinal getSituacionFinal() {
        return situacionFinal;
    }

    public void setSituacionFinal(SituacionFinal situacionFinal) {
        this.situacionFinal = situacionFinal;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    // toString seguro: sólo ids

    @Override
    public String toString() {
        return "Matricula{" +
                "id=" + getId() +
                ", estudianteId=" + (estudiante != null ? estudiante.getId() : null) +
                ", anioId=" + (anio != null ? anio.getId() : null) +
                ", seccionId=" + (seccion != null ? seccion.getId() : null) +
                ", fechaMatricula=" + fechaMatricula +
                ", situacionFinal=" + situacionFinal +
                '}';
    }
}
