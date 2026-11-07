package com.educa360.backend.dto;

import java.math.BigDecimal;

/** Promedio anual de un curso dentro del cierre. */
public record CierreCursoResponse(
        Long cursoId,
        String materiaNombre,
        BigDecimal promedioAnual,
        boolean aprobado,
        boolean tieneNotas
) {
}
