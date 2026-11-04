package com.educa360.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Historial de notas y promedios de un alumno.
 *
 * Sin {@code bimestreId} el ámbito es el año completo: {@code promediosCurso}
 * trae el promedio anual por curso y {@code promedioGeneral} el promedio de
 * esos anuales. Con {@code bimestreId}, el ámbito es ese bimestre.
 */
public record CalificacionResumenResponse(
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        String seccionEtiqueta,
        Long anioId,
        Integer anio,
        Long bimestreId,
        String bimestreNombre,
        List<CalificacionDetalleResponse> detalle,
        List<PromedioUnidadResponse> promediosUnidad,
        List<PromedioCursoResponse> promediosCurso,
        BigDecimal promedioGeneral
) {
}
