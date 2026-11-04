package com.educa360.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** Fila de un curso en la boleta: promedio anual y desglose por bimestre. */
public record BoletaCursoResponse(
        Long cursoId,
        String materiaNombre,
        BigDecimal promedioAnual,
        boolean aprobado,
        List<BoletaNotaBimestreResponse> bimestres
) {
}
