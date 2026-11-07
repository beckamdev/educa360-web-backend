package com.educa360.backend.dto;

import java.util.List;

/** Reporte de asistencia de un año (opcionalmente por bimestre y sección). */
public record ReporteAsistenciaResponse(
        Long anioId,
        Integer anio,
        Long bimestreId,
        String bimestreNombre,
        Long seccionId,
        String seccionEtiqueta,
        List<ReporteAsistenciaFilaResponse> alumnos
) {
}
