package com.educa360.backend.dto;

import com.educa360.backend.entity.Calificacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una nota dentro del historial de un alumno o de la hoja de una unidad. */
public record CalificacionDetalleResponse(
        Long id,
        Long unidadId,
        Integer unidadNumero,
        String unidadTitulo,
        Long bimestreId,
        Integer bimestreNumero,
        String bimestreNombre,
        Long cursoId,
        String materiaNombre,
        String criterio,
        BigDecimal nota,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CalificacionDetalleResponse desde(Calificacion calificacion) {
        return new CalificacionDetalleResponse(
                calificacion.getId(),
                calificacion.getUnidad().getId(),
                calificacion.getUnidad().getNumero(),
                calificacion.getUnidad().getTitulo(),
                calificacion.getUnidad().getBimestre().getId(),
                calificacion.getUnidad().getBimestre().getNumero(),
                calificacion.getUnidad().getBimestre().getNombre(),
                calificacion.getUnidad().getCurso().getId(),
                calificacion.getUnidad().getCurso().getMateria().getNombre(),
                calificacion.getCriterio(),
                calificacion.getNota(),
                calificacion.getCreatedAt(),
                calificacion.getUpdatedAt()
        );
    }
}
