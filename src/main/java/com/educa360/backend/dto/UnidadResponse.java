package com.educa360.backend.dto;

import com.educa360.backend.entity.Unidad;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una unidad con sus contenidos ordenados.
 *
 * El curso se describe con materia y sección para que el panel no tenga que
 * cruzar otra llamada. Los contenidos viajan aquí porque publicar una unidad
 * publica también su planificación completa.
 */
public record UnidadResponse(
        Long id,
        Long cursoId,
        String materiaNombre,
        String seccionEtiqueta,
        Long bimestreId,
        String bimestreNombre,
        Integer numero,
        String titulo,
        String descripcion,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Unidad.Estado estado,
        List<ContenidoResponse> contenidos,
        LocalDateTime createdAt
) {
    public static UnidadResponse desde(Unidad unidad) {
        return new UnidadResponse(
                unidad.getId(),
                unidad.getCurso().getId(),
                unidad.getCurso().getMateria().getNombre(),
                unidad.getCurso().getSeccion().getEtiqueta(),
                unidad.getBimestre().getId(),
                unidad.getBimestre().getNombre(),
                unidad.getNumero(),
                unidad.getTitulo(),
                unidad.getDescripcion(),
                unidad.getFechaInicio(),
                unidad.getFechaFin(),
                unidad.getEstado(),
                unidad.getContenidos().stream().map(ContenidoResponse::desde).toList(),
                unidad.getCreatedAt()
        );
    }
}
