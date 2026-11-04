package com.educa360.backend.dto;

import java.util.List;

/** Fila de la hoja de notas: un alumno y sus criterios registrados en la unidad. */
public record CalificacionAlumnoResponse(
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        List<CalificacionDetalleResponse> calificaciones
) {
}
