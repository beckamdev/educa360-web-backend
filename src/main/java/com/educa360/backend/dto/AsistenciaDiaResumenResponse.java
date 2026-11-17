package com.educa360.backend.dto;

import com.educa360.backend.entity.DiaSemana;

import java.time.LocalDate;
import java.util.List;

/**
 * Resumen del día del docente: todos sus bloques de esa fecha con el avance
 * de cada uno, en una sola petición.
 *
 * Evita que el móvil tenga que abrir la hoja de cada bloque para saber qué
 * falta. {@code dia} llega en null los domingos (no hay horario) y
 * {@code bloques} vacío cuando el docente no dicta ese día. Un bloque está
 * completo cuando {@code pendientes == 0}; {@code bloquesCompletos} los cuenta
 * para pintar el progreso del día.
 */
public record AsistenciaDiaResumenResponse(
        LocalDate fecha,
        DiaSemana dia,
        int totalBloques,
        int bloquesCompletos,
        List<AsistenciaBloqueResumenResponse> bloques
) {
}
