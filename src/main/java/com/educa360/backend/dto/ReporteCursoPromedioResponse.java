package com.educa360.backend.dto;

import java.math.BigDecimal;

/** Promedio de un curso en el reporte de calificaciones. */
public record ReporteCursoPromedioResponse(
        Long cursoId,
        String materiaNombre,
        BigDecimal promedio,
        boolean aprobado
) {
}
