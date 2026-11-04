package com.educa360.backend.dto;

import com.educa360.backend.entity.Matricula;

import java.math.BigDecimal;

/** Un año del historial del alumno: sección, situación, promedio y asistencia. */
public record HistorialAnioResponse(
        Long matriculaId,
        Long anioId,
        Integer anio,
        String anioNombre,
        Long seccionId,
        String seccionEtiqueta,
        Integer grado,
        Matricula.SituacionFinal situacionFinal,
        BigDecimal promedioGeneral,
        BigDecimal asistenciaPorcentaje,
        int totalAsistencias,
        int asistenciasEfectivas
) {
}
