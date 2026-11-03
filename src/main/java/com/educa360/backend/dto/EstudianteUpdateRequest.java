package com.educa360.backend.dto;

import com.educa360.backend.entity.Estudiante;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Edición acotada de la ficha del alumno por Secretaría.
 *
 * Sólo fecha de nacimiento y estado académico: los datos de la cuenta
 * (nombres, DNI, email) se gestionan desde usuarios y no se tocan aquí.
 */
public record EstudianteUpdateRequest(
        @NotNull(message = "La fecha de nacimiento es obligatoria")
        LocalDate fechaNacimiento,

        Estudiante.EstadoEstudiante estado
) {
}
