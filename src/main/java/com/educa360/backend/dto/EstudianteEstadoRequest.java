package com.educa360.backend.dto;

import com.educa360.backend.entity.Estudiante;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio del estado académico del alumno.
 *
 * ACTIVO es el reingreso: reactiva al alumno y, si su matrícula del año
 * vigente estaba RETIRADA, la vuelve a EN_CURSO. RETIRADO marca también
 * la matrícula vigente. TRASLADADO y EGRESADO sólo cambian al alumno.
 */
public record EstudianteEstadoRequest(
        @NotNull(message = "El estado es obligatorio")
        Estudiante.EstadoEstudiante estado
) {
}
