package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Unidad: la planificación de un curso dentro de un bimestre.
 *
 * Es la intersección entre un {@link Curso} y un {@link Bimestre}: agrupa
 * contenidos y, en M6, calificaciones. No es una clase dictada ni una sesión
 * real; es la hoja de ruta que el docente publica para sus alumnos.
 *
 * REGLAS (en PlanificacionService):
 * - el bimestre debe pertenecer al mismo año que la sección del curso;
 * - sólo se planifica en bimestres PLANIFICADO o ABIERTO, nunca CERRADO;
 * - el número es automático y único por (curso, bimestre);
 * - nace BORRADOR; PUBLICADA la hace visible a estudiantes y apoderados.
 */
@Entity
@Table(name = "unidades",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"curso_id", "bimestre_id", "numero"}))
public class Unidad extends Auditable {

    public enum Estado {
        BORRADOR,
        PUBLICADA
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bimestre_id", nullable = false)
    private Bimestre bimestre;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descripcion;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    // Los contenidos se ordenan por su posición dentro de la unidad.
    // La baja de la unidad los arrastra: no tienen vida propia fuera de ella.
    @OneToMany(mappedBy = "unidad", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<Contenido> contenidos = new ArrayList<>();

    // Constructors

    public Unidad() {
        this.estado = Estado.BORRADOR;
    }

    public Unidad(Curso curso, Bimestre bimestre, Integer numero, String titulo) {
        this();
        this.curso = curso;
        this.bimestre = bimestre;
        this.numero = numero;
        this.titulo = titulo;
    }

    // Helpers

    public boolean estaPublicada() {
        return estado == Estado.PUBLICADA;
    }

    public boolean estaEnBorrador() {
        return estado == Estado.BORRADOR;
    }

    // Getters and Setters

    public Curso getCurso() {
        return curso;
    }

    public void setCurso(Curso curso) {
        this.curso = curso;
    }

    public Bimestre getBimestre() {
        return bimestre;
    }

    public void setBimestre(Bimestre bimestre) {
        this.bimestre = bimestre;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
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

    public List<Contenido> getContenidos() {
        return contenidos;
    }

    // toString seguro: sólo ids y campos simples (sin colecciones lazy)

    @Override
    public String toString() {
        return "Unidad{" +
                "id=" + getId() +
                ", cursoId=" + (curso != null ? curso.getId() : null) +
                ", bimestreId=" + (bimestre != null ? bimestre.getId() : null) +
                ", numero=" + numero +
                ", titulo='" + titulo + '\'' +
                ", estado=" + estado +
                '}';
    }
}
