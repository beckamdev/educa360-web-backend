package com.educa360.backend.dto;

import java.math.BigDecimal;

/** Promedio de una unidad dentro del acta de un curso. */
public record ActaUnidadResponse(
        Long unidadId,
        Integer numero,
        String titulo,
        BigDecimal promedio
) {
}
