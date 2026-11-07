package com.educa360.backend.dto;

import com.educa360.backend.entity.Unidad;
import jakarta.validation.constraints.NotNull;

/** Publicar o devolver a borrador una unidad. */
public record UnidadEstadoRequest(
        @NotNull(message = "El estado es obligatorio")
        Unidad.Estado estado
) {
}
