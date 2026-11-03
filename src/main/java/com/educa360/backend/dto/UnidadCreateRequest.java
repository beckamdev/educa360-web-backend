package com.educa360.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Alta de una unidad desde la zona del docente.
 *
 * El curso sale de la ruta y el número lo asigna el servicio: el request no
 * puede inventar ni duplicar la posición de la unidad dentro del bimestre.
 */
public record UnidadCreateRequest(
        @NotNull(message = "El bimestre es obligatorio")
        Long bimestreId,

        @NotBlank(message = "El título de la unidad es obligatorio")
        String titulo,

        String descripcion,

        LocalDate fechaInicio,

        LocalDate fechaFin
) {
}
