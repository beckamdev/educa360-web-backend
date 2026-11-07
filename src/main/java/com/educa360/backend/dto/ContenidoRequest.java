package com.educa360.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** Alta o edición de un contenido de la unidad. El orden lo maneja el servicio. */
public record ContenidoRequest(
        @NotBlank(message = "El título del contenido es obligatorio")
        String titulo,

        String descripcion,

        LocalDate fechaPrevista
) {
}
