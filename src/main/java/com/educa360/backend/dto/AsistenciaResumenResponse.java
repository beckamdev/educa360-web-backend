package com.educa360.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Historial y resumen de asistencia de un alumno.
 *
 * El porcentaje se calcula sobre las asistencias registradas:
 * {@code efectivas / total}. Son efectivas PRESENTE, TARDANZA y JUSTIFICADA;
 * sólo FALTA es inasistencia injustificada. El mínimo para aprobar es 70%.
 */
public record AsistenciaResumenResponse(
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        String seccionEtiqueta,
        Long anioId,
        Integer anio,
        Long bimestreId,
        String bimestreNombre,
        int total,
        int presentes,
        int tardanzas,
        int justificadas,
        int faltas,
        int efectivas,
        BigDecimal porcentaje,
        List<AsistenciaDetalleResponse> detalle
) {
}
