package com.educa360.backend.dto;

import java.util.List;

/** Reporte de calificaciones de un año (opcionalmente por bimestre y sección). */
public record ReporteCalificacionesResponse(
        Long anioId,
        Integer anio,
        Long bimestreId,
        String bimestreNombre,
        Long seccionId,
        String seccionEtiqueta,
        List<ReporteCalificacionFilaResponse> alumnos
) {
}
