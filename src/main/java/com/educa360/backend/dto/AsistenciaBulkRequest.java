package com.educa360.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Hoja de asistencia de un bloque en una fecha.
 *
 * Es un upsert parcial: sólo viajan los alumnos que se registran o corrigen;
 * los que no aparecen conservan su estado actual. No hay borrado.
 */
public record AsistenciaBulkRequest(
        @NotNull(message = "El bloque es obligatorio")
        Long bloqueId,

        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha,

        @NotEmpty(message = "La lista de asistencias no puede estar vacía")
        List<@Valid @NotNull AsistenciaItemRequest> asistencias
) {
}
