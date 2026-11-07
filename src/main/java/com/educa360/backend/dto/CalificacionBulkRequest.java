package com.educa360.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Hoja de notas de una unidad.
 *
 * Es un upsert parcial: sólo viajan las notas que se registran o corrigen,
 * identificadas por (matrícula, criterio). Las que no aparecen no se tocan.
 */
public record CalificacionBulkRequest(
        @NotEmpty(message = "La lista de calificaciones no puede estar vacía")
        List<@Valid @NotNull CalificacionItemRequest> calificaciones
) {
}
