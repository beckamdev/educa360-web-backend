package com.educa360.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Edición de los datos de una unidad.
 *
 * No incluye el bimestre: mover una unidad de bimestre no es una edición,
 * es otra planificación. Para eso se elimina y se crea de nuevo.
 */
public record UnidadUpdateRequest(
        @NotBlank(message = "El título de la unidad es obligatorio")
        String titulo,

        String descripcion,

        LocalDate fechaInicio,

        LocalDate fechaFin
) {
}
