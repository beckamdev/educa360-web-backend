package com.educa360.backend.dto;

import com.educa360.backend.entity.Unidad;

import java.util.List;

/**
 * Hoja de notas de una unidad.
 *
 * Incluye a todos los alumnos activos de la sección; los que todavía no
 * tienen notas llegan con la lista vacía.
 */
public record CalificacionUnidadResponse(
        Long unidadId,
        Integer numero,
        String titulo,
        Unidad.Estado estado,
        Long cursoId,
        String materiaNombre,
        String seccionEtiqueta,
        Long bimestreId,
        String bimestreNombre,
        List<CalificacionAlumnoResponse> alumnos
) {
}
