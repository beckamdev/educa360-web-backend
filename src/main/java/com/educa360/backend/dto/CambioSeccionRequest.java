package com.educa360.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cambio de sección de una matrícula.
 *
 * Sólo se permite antes del primer día de clases del año y mientras la
 * matrícula no tenga asistencias ni calificaciones. La sección nueva debe
 * pertenecer al mismo año.
 */
public record CambioSeccionRequest(
        @NotNull(message = "La sección nueva es obligatoria")
        Long seccionId
) {
}
