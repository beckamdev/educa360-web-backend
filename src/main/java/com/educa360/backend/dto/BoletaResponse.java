package com.educa360.backend.dto;

import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Matricula;

import java.math.BigDecimal;
import java.util.List;

/**
 * Boleta anual calculada (no se guarda): cursos × bimestres, promedio anual,
 * asistencia y situación final de la matrícula.
 */
public record BoletaResponse(
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        Long anioId,
        Integer anio,
        String anioNombre,
        AnioEscolar.Estado anioEstado,
        Long seccionId,
        String seccionEtiqueta,
        Integer grado,
        Matricula.SituacionFinal situacionFinal,
        BigDecimal promedioGeneral,
        BigDecimal asistenciaPorcentaje,
        int totalAsistencias,
        int asistenciasEfectivas,
        int faltas,
        List<BoletaBimestreResponse> bimestres,
        List<BoletaCursoResponse> cursos
) {
}
