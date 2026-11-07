package com.educa360.backend.dto;

import com.educa360.backend.entity.Estudiante;

import java.util.List;

/** Historial académico del alumno, año por año, para Secretaría. */
public record HistorialEstudianteResponse(
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        Estudiante.EstadoEstudiante estado,
        List<HistorialAnioResponse> anios
) {
}
