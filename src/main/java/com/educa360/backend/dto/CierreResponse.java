package com.educa360.backend.dto;

import com.educa360.backend.entity.AnioEscolar;

import java.util.List;

/**
 * Resultado del cierre anual: la misma vista sirve para el preview y para el
 * POST de cierre, de modo que el administrador vea exactamente lo que se va a
 * aplicar.
 *
 * {@code listo=false} indica que el cierre está bloqueado (bimestres abiertos o
 * notas faltantes) y {@code bloqueos} explica el motivo.
 */
public record CierreResponse(
        Long anioId,
        Integer anio,
        String anioNombre,
        AnioEscolar.Estado anioEstado,
        boolean listo,
        int bimestres,
        int bimestresCerrados,
        int aprobados,
        int desaprobados,
        int retirados,
        int conNotasFaltantes,
        List<String> bloqueos,
        List<CierreAlumnoResponse> alumnos
) {
}
