package com.educa360.backend.dto;

import com.educa360.backend.entity.Matricula;

import java.math.BigDecimal;

/** Fila del reporte de asistencia por alumno. */
public record ReporteAsistenciaFilaResponse(
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        String seccionEtiqueta,
        int total,
        int presentes,
        int tardanzas,
        int justificadas,
        int faltas,
        int efectivas,
        BigDecimal porcentaje,
        Matricula.SituacionFinal situacionFinal
) {
}
