package com.educa360.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** Fila del acta: un alumno, sus promedios por unidad y el del bimestre. */
public record ActaAlumnoResponse(
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        List<ActaUnidadResponse> unidades,
        BigDecimal promedioBimestre
) {
}
