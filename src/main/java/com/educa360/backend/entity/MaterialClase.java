package com.educa360.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Material de clase: archivo que el docente publica en uno de sus cursos.
 *
 * El archivo vive en el bucket privado {@code materiales} de Supabase Storage;
 * aquí sólo se guarda su ruta ({@code storagePath}), nunca una URL pública.
 * Puede estar ligado a un {@link Contenido} de una unidad, o colgar
 * directamente del curso.
 *
 * REGLAS (en MaterialService):
 * - sólo el docente dueño del curso crea, edita, publica o da de baja;
 * - nace NO publicado; se publica con una acción explícita;
 * - un material no publicado sólo lo ven el docente y ADMIN;
 * - si cuelga de una unidad BORRADOR, el alumno no lo ve aunque esté publicado;
 * - la baja es lógica y elimina el objeto del bucket.
 */
@Entity
@Table(name = "materiales_clase")
public class MaterialClase extends Auditable {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    /** Opcional: si existe, debe pertenecer al mismo curso. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contenido_id")
    private Contenido contenido;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, length = 255)
    private String nombreOriginal;

    @Column(nullable = false, length = 150)
    private String mimeType;

    @Column(nullable = false)
    private Long tamanoBytes;

    @Column(nullable = false)
    private boolean publicado;

    private LocalDateTime fechaPublicacion;

    @Column(nullable = false)
    private boolean activo = true;

    // Constructors

    public MaterialClase() {
        this.activo = true;
        this.publicado = false;
    }

    public MaterialClase(Curso curso, Docente docente, Contenido contenido,
                         String titulo, String storagePath, String nombreOriginal,
                         String mimeType, Long tamanoBytes) {
        this();
        this.curso = curso;
        this.docente = docente;
        this.contenido = contenido;
        this.titulo = titulo;
        this.storagePath = storagePath;
        this.nombreOriginal = nombreOriginal;
        this.mimeType = mimeType;
        this.tamanoBytes = tamanoBytes;
    }

    // Helpers

    public void publicar() {
        this.publicado = true;
        this.fechaPublicacion = LocalDateTime.now();
    }

    public void despublicar() {
        this.publicado = false;
        this.fechaPublicacion = null;
    }

    public boolean esVisibleParaAlumno() {
        return activo && publicado
                && (contenido == null || contenido.getUnidad().estaPublicada());
    }

    // Getters and Setters

    public Curso getCurso() {
        return curso;
    }

    public void setCurso(Curso curso) {
        this.curso = curso;
    }

    public Docente getDocente() {
        return docente;
    }

    public void setDocente(Docente docente) {
        this.docente = docente;
    }

    public Contenido getContenido() {
        return contenido;
    }

    public void setContenido(Contenido contenido) {
        this.contenido = contenido;
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

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public void setNombreOriginal(String nombreOriginal) {
        this.nombreOriginal = nombreOriginal;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getTamanoBytes() {
        return tamanoBytes;
    }

    public void setTamanoBytes(Long tamanoBytes) {
        this.tamanoBytes = tamanoBytes;
    }

    public boolean isPublicado() {
        return publicado;
    }

    public void setPublicado(boolean publicado) {
        this.publicado = publicado;
    }

    public LocalDateTime getFechaPublicacion() {
        return fechaPublicacion;
    }

    public void setFechaPublicacion(LocalDateTime fechaPublicacion) {
        this.fechaPublicacion = fechaPublicacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    // toString seguro: sólo ids y campos simples

    @Override
    public String toString() {
        return "MaterialClase{" +
                "id=" + getId() +
                ", cursoId=" + (curso != null ? curso.getId() : null) +
                ", docenteId=" + (docente != null ? docente.getId() : null) +
                ", contenidoId=" + (contenido != null ? contenido.getId() : null) +
                ", titulo='" + titulo + '\'' +
                ", publicado=" + publicado +
                ", activo=" + activo +
                '}';
    }
}
