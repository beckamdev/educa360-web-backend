package com.educa360.backend.dto;

import java.math.BigDecimal;

/** Promedio de un curso en un bimestre; null si no hay notas. */
public record BoletaNotaBimestreResponse(
        Long bimestreId,
        Integer numero,
        String nombre,
        BigDecimal promedio
) {
}
