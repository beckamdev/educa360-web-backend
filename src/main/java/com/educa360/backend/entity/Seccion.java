package com.educa360.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Sección (grupo de alumnos) de un año escolar: "3°A".
 *
 * SÓLO SECUNDARIA: el grado va de 1 a 5 y no existe campo "nivel" — el
 * sistema es secundaria, así que la columna sería redundante.
 *
 * Pertenece a UN año escolar y no se mueve de año: arrastrarla rompería el
 * histórico de matrículas, asistencia y notas. Si se necesita otro año, se
 * crea otra sección.
 */
@Entity
@Table(name = "secciones",
        uniqueConstraints = @UniqueConstraint(columnNames = {"anio_id", "grado", "letra"}))
public class Seccion extends Auditable {

    @Min(value = 1, message = "El grado va de 1 a 5 (secundaria)")
    @Max(value = 5, message = "El grado va de 1 a 5 (secundaria)")
    @Column(nullable = false)
    private Integer grado;

    @NotBlank
    @Pattern(regexp = "[A-Z]", message = "La letra debe ser una mayúscula (A, B, C...)")
    @Column(nullable = false)
    private String letra;

    @Column
    private String aula; // texto libre: "A-101", "Laboratorio 2"...

    // Docente a cargo del grupo (tutoría). Opcional: una sección puede no tener aún.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id")
    private Docente tutor;

    // La sección pertenece a UN año (por eso Curso no repite año).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "anio_id", nullable = false)
    private AnioEscolar anio;

    // Constructors

    public Seccion() {
    }

    public Seccion(Integer grado, String letra, String aula, Docente tutor, AnioEscolar anio) {
        this();
        this.grado = grado;
        this.letra = letra;
        this.aula = aula;
        this.tutor = tutor;
        this.anio = anio;
    }

    // Helpers

    /** Etiqueta visible para paneles y listados: "3°A". */
    public String getEtiqueta() {
        return grado + "°" + letra;
    }

    // Getters and Setters

    public Integer getGrado() {
        return grado;
    }

    public void setGrado(Integer grado) {
        this.grado = grado;
    }

    public String getLetra() {
        return letra;
    }

    public void setLetra(String letra) {
        this.letra = letra;
    }

    public String getAula() {
        return aula;
    }

    public void setAula(String aula) {
        this.aula = aula;
    }

    public Docente getTutor() {
        return tutor;
    }

    public void setTutor(Docente tutor) {
        this.tutor = tutor;
    }

    public AnioEscolar getAnio() {
        return anio;
    }

    public void setAnio(AnioEscolar anio) {
        this.anio = anio;
    }

    // toString seguro para logs: sin colecciones ni entidades cruzadas

    @Override
    public String toString() {
        return "Seccion{" +
                "id=" + getId() +
                ", grado=" + grado +
                ", letra='" + letra + '\'' +
                ", tutorId=" + (tutor != null ? tutor.getId() : null) +
                ", anioId=" + (anio != null ? anio.getId() : null) +
                '}';
    }
}
