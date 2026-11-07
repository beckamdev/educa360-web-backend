package com.educa360.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

/**
 * Calificación de un alumno en una unidad, identificada por un criterio libre.
 *
 * Cuelga de la {@link Matricula} y de la {@link Unidad}: así queda atada al
 * año, a la sección, al curso y al bimestre correctos. No existe una entidad
 * "Evaluacion": el criterio ("Examen parcial", "Práctica 1") vive aquí.
 *
 * REGLAS (en CalificacionService):
 * - única por (matrícula, unidad, criterio);
 * - la matrícula debe pertenecer a la sección del curso de la unidad;
 * - sólo el docente del curso registra, corrige o elimina;
 * - el bimestre de la unidad debe estar ABIERTO;
 * - escala vigesimal estricta, dos decimales;
 * - el alumno y el apoderado sólo ven notas de unidades PUBLICADA.
 */
@Entity
@Table(name = "calificaciones",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"matricula_id", "unidad_id", "criterio"}))
@Check(constraints = "nota >= 0 and nota <= 20")
public class Calificacion extends Auditable {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unidad_id", nullable = false)
    private Unidad unidad;

    @Column(nullable = false, length = 120)
    private String criterio;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal nota;

    /** Quién registró o corrigió por última vez. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Docente registradoPor;

    // Constructors

    public Calificacion() {
    }

    public Calificacion(Matricula matricula, Unidad unidad, String criterio) {
        this();
        this.matricula = matricula;
        this.unidad = unidad;
        this.criterio = criterio;
    }

    // Getters and Setters

    public Matricula getMatricula() {
        return matricula;
    }

    public void setMatricula(Matricula matricula) {
        this.matricula = matricula;
    }

    public Unidad getUnidad() {
        return unidad;
    }

    public void setUnidad(Unidad unidad) {
        this.unidad = unidad;
    }

    public String getCriterio() {
        return criterio;
    }

    public void setCriterio(String criterio) {
        this.criterio = criterio;
    }

    public BigDecimal getNota() {
        return nota;
    }

    public void setNota(BigDecimal nota) {
        this.nota = nota;
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
        return "Calificacion{" +
                "id=" + getId() +
                ", matriculaId=" + (matricula != null ? matricula.getId() : null) +
                ", unidadId=" + (unidad != null ? unidad.getId() : null) +
                ", criterio='" + criterio + '\'' +
                ", nota=" + nota +
                '}';
    }
}
