package com.educa360.backend.dto;

import com.educa360.backend.entity.Contenido;

import java.time.LocalDate;

/** Un tema planificado dentro de una unidad. */
public record ContenidoResponse(
        Long id,
        Long unidadId,
        Integer orden,
        String titulo,
        String descripcion,
        LocalDate fechaPrevista
) {
    public static ContenidoResponse desde(Contenido contenido) {
        return new ContenidoResponse(
                contenido.getId(),
                contenido.getUnidad().getId(),
                contenido.getOrden(),
                contenido.getTitulo(),
                contenido.getDescripcion(),
                contenido.getFechaPrevista()
        );
    }
}
