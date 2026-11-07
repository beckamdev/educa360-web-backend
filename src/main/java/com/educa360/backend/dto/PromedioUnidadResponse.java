package com.educa360.backend.dto;

import java.math.BigDecimal;

/** Promedio simple de una unidad para un alumno. */
public record PromedioUnidadResponse(
        Long unidadId,
        Integer unidadNumero,
        String unidadTitulo,
        Long bimestreId,
        Integer bimestreNumero,
        String bimestreNombre,
        Long cursoId,
        String materiaNombre,
        BigDecimal promedio,
        int cantidad
) {
}
