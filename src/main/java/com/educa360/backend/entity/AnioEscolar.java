package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Año escolar: el ancla anual de la estructura académica.
 *
 * Reemplaza al antiguo {@code PeriodoAcademico}. Un año agrupa sus bimestres,
 * secciones y matrículas. Nace {@code PLANIFICADO} (editable) y se abre o
 * cierra de forma explícita por ADMIN.
 *
 * Solo guarda {@code fechaInicio} (primer día de clases). El cierre del año
 * lo marcan sus bimestres: el último bimestre define el último día de clases.
 */
@Entity
@Table(name = "anios_escolares",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "anio"),
                @UniqueConstraint(columnNames = "nombre")
        })
public class AnioEscolar extends Auditable {

    public enum Estado {
        PLANIFICADO,
        ABIERTO,
        CERRADO
    }

    @Column(nullable = false)
    private Integer anio; // 2026

    @Column(nullable = false)
    private String nombre; // "Año Escolar 2026"

    @Column(nullable = false)
    private LocalDate fechaInicio; // primer día de clases

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    // Constructors

    public AnioEscolar() {
        this.estado = Estado.PLANIFICADO;
    }

    public AnioEscolar(Integer anio, String nombre, LocalDate fechaInicio) {
        this();
        this.anio = anio;
        this.nombre = nombre;
        this.fechaInicio = fechaInicio;
    }

    // Helpers

    public boolean estaAbierto() {
        return estado == Estado.ABIERTO;
    }

    public boolean estaCerrado() {
        return estado == Estado.CERRADO;
    }

    // Getters and Setters

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        return "AnioEscolar{" +
                "id=" + getId() +
                ", anio=" + anio +
                ", nombre='" + nombre + '\'' +
                ", fechaInicio=" + fechaInicio +
                ", estado=" + estado +
                '}';
    }
}
