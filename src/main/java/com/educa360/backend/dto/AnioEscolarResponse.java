package com.educa360.backend.dto;

import com.educa360.backend.entity.AnioEscolar;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Ficha de un año escolar para el panel de ADMIN. */
public record AnioEscolarResponse(
        Long id,
        Integer anio,
        String nombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        AnioEscolar.Estado estado,
        LocalDateTime createdAt
) {
    public static AnioEscolarResponse desde(AnioEscolar anio) {
        return new AnioEscolarResponse(
                anio.getId(),
                anio.getAnio(),
                anio.getNombre(),
                anio.getFechaInicio(),
                anio.getFechaFin(),
                anio.getEstado(),
                anio.getCreatedAt()
        );
    }
}
