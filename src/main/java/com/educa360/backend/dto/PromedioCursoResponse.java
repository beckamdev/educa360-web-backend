package com.educa360.backend.dto;

import java.math.BigDecimal;

/** Promedio de un curso en el ámbito consultado (bimestre o año). */
public record PromedioCursoResponse(
        Long cursoId,
        String materiaNombre,
        BigDecimal promedio
) {
}
