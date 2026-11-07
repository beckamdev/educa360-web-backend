package com.educa360.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Alta de matrícula desde Secretaría: "inscribe a este alumno en esta sección".
 *
 * El año sale de la sección; no viaja en el request para no permitir
 * combinaciones incoherentes (sección de un año, matrícula de otro).
 * La matrícula nace EN_CURSO; su situación final la fija el retiro o el
 * cierre del año.
 */
public record MatriculaRequest(
        @NotNull(message = "El estudiante es obligatorio")
        Long estudianteId,

        @NotNull(message = "La sección es obligatoria")
        Long seccionId,

        @NotNull(message = "La fecha de matrícula es obligatoria")
        LocalDate fechaMatricula,

        String observacion
) {
}
