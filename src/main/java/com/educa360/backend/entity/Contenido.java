package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Contenido: un tema o subtema planificado dentro de una {@link Unidad}.
 *
 * No es una clase dictada ni una sesión con fecha obligatoria: es la lista
 * ordenada de temas que el docente planea cubrir. Los materiales de clase
 * (M8) colgarán de aquí.
 */
@Entity
@Table(name = "contenidos",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"unidad_id", "orden"}))
public class Contenido extends Auditable {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unidad_id", nullable = false)
    private Unidad unidad;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descripcion;

    private LocalDate fechaPrevista;

    // Constructors

    public Contenido() {
    }

    public Contenido(Unidad unidad, Integer orden, String titulo) {
        this();
        this.unidad = unidad;
        this.orden = orden;
        this.titulo = titulo;
    }

    // Getters and Setters

    public Unidad getUnidad() {
        return unidad;
    }

    public void setUnidad(Unidad unidad) {
        this.unidad = unidad;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDate getFechaPrevista() {
        return fechaPrevista;
    }

    public void setFechaPrevista(LocalDate fechaPrevista) {
        this.fechaPrevista = fechaPrevista;
    }

    // toString seguro: sólo ids y campos simples

    @Override
    public String toString() {
        return "Contenido{" +
                "id=" + getId() +
                ", unidadId=" + (unidad != null ? unidad.getId() : null) +
                ", orden=" + orden +
                ", titulo='" + titulo + '\'' +
                ", fechaPrevista=" + fechaPrevista +
                '}';
    }
}
