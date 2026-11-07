package com.educa360.backend.dto;

/** Columna de bimestre en la boleta (id, número y nombre). */
public record BoletaBimestreResponse(
        Long bimestreId,
        Integer numero,
        String nombre
) {
}
