package com.educa360.backend.dto;

import com.educa360.backend.entity.Matricula;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fila del cierre para un alumno.
 *
 * {@code situacionPropuesta} es null cuando faltan notas y el cierre está
 * bloqueado para ese alumno; un alumno retirado siempre propone RETIRADO y no
 * bloquea el cierre por notas.
 */
public record CierreAlumnoResponse(
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        Long seccionId,
        String seccionEtiqueta,
        Integer grado,
        Matricula.SituacionFinal situacionActual,
        Matricula.SituacionFinal situacionPropuesta,
        boolean faltanNotas,
        BigDecimal promedioGeneral,
        BigDecimal asistenciaPorcentaje,
        List<CierreCursoResponse> cursos
) {
}
