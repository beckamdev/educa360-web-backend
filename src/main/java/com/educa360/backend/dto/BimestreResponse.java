package com.educa360.backend.dto;

import com.educa360.backend.entity.Bimestre;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Ficha de un bimestre para el panel de ADMIN. */
public record BimestreResponse(
        Long id,
        Long anioId,
        String anioNombre,
        Integer numero,
        String nombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Bimestre.Estado estado,
        LocalDateTime createdAt
) {
    public static BimestreResponse desde(Bimestre bimestre) {
        return new BimestreResponse(
                bimestre.getId(),
                bimestre.getAnio().getId(),
                bimestre.getAnio().getNombre(),
                bimestre.getNumero(),
                bimestre.getNombre(),
                bimestre.getFechaInicio(),
                bimestre.getFechaFin(),
                bimestre.getEstado(),
                bimestre.getCreatedAt()
        );
    }
}
