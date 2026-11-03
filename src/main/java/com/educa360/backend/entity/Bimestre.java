package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Bimestre de un año escolar. Divide el año en ventanas de evaluación.
 *
 * El nombre es derivado del número ("Bimestre I".."Bimestre IV"): no se
 * persiste para que no pueda quedar desincronizado.
 */
@Entity
@Table(name = "bimestres",
        uniqueConstraints = @UniqueConstraint(columnNames = {"anio_id", "numero"}))
public class Bimestre extends Auditable {

    public enum Estado {
        PLANIFICADO,
        ABIERTO,
        CERRADO
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "anio_id", nullable = false)
    private AnioEscolar anio;

    @Column(nullable = false)
    private Integer numero; // 1..4

    @Column(nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false)
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    // Constructors

    public Bimestre() {
        this.estado = Estado.PLANIFICADO;
    }

    public Bimestre(AnioEscolar anio, Integer numero, LocalDate fechaInicio, LocalDate fechaFin) {
        this();
        this.anio = anio;
        this.numero = numero;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }

    // Helpers

    /** "Bimestre I" ... "Bimestre IV". */
    public String getNombre() {
        return "Bimestre " + romano(numero);
    }

    public boolean estaAbierto() {
        return estado == Estado.ABIERTO;
    }

    public boolean estaCerrado() {
        return estado == Estado.CERRADO;
    }

    private static String romano(Integer numero) {
        if (numero == null) {
            return "";
        }
        return switch (numero) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(numero);
        };
    }

    // Getters and Setters

    public AnioEscolar getAnio() {
        return anio;
    }

    public void setAnio(AnioEscolar anio) {
        this.anio = anio;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        return "Bimestre{" +
                "id=" + getId() +
                ", anioId=" + (anio != null ? anio.getId() : null) +
                ", numero=" + numero +
                ", fechaInicio=" + fechaInicio +
                ", fechaFin=" + fechaFin +
                ", estado=" + estado +
                '}';
    }
}
