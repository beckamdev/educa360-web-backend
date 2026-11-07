package com.educa360.backend.dto;

import com.educa360.backend.entity.Matricula;

import java.math.BigDecimal;
import java.util.List;

/** Fila del reporte de calificaciones: cursos y promedio general del alumno. */
public record ReporteCalificacionFilaResponse(
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        String seccionEtiqueta,
        List<ReporteCursoPromedioResponse> cursos,
        BigDecimal promedioGeneral,
        Matricula.SituacionFinal situacionFinal
) {
}
