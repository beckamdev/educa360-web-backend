package com.educa360.backend.entity;

import jakarta.persistence.*;

/**
 * Comunicado: mensaje simple de una persona a toda la institución o a una
 * sección.
 *
 * No tiene título ni tipo: el alcance se deduce de {@code seccion}. Si es
 * {@code null}, el mensaje es general para el año; si tiene sección, solo lo
 * ven esa sección y sus familias. El autor y la fecha/hora salen de
 * {@link Auditable} y de la relación con {@link User}.
 *
 * REGLAS (en ComunicadoService):
 * - ADMIN publica generales o por sección;
 * - SECRETARIA publica solo generales;
 * - DOCENTE publica solo en secciones donde dicta clase o es tutor;
 * - alumno y apoderado solo leen generales y los de su sección;
 * - la baja es lógica.
 */
@Entity
@Table(name = "comunicados")
public class Comunicado extends Auditable {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private User autor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "anio_id", nullable = false)
    private AnioEscolar anio;

    /** {@code null} = general para todo el año. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seccion_id")
    private Seccion seccion;

    @Column(nullable = false, columnDefinition = "text")
    private String contenido;

    @Column(nullable = false)
    private boolean activo = true;

    // Constructors

    public Comunicado() {
        this.activo = true;
    }

    public Comunicado(User autor, AnioEscolar anio, Seccion seccion, String contenido) {
        this();
        this.autor = autor;
        this.anio = anio;
        this.seccion = seccion;
        this.contenido = contenido;
    }

    // Helpers

    public boolean esGeneral() {
        return seccion == null;
    }

    // Getters and Setters

    public User getAutor() {
        return autor;
    }

    public void setAutor(User autor) {
        this.autor = autor;
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

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return "Comunicado{" +
                "id=" + getId() +
                ", autorId=" + (autor != null ? autor.getId() : null) +
                ", anioId=" + (anio != null ? anio.getId() : null) +
                ", seccionId=" + (seccion != null ? seccion.getId() : null) +
                ", activo=" + activo +
                '}';
    }
}
