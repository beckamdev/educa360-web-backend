package com.educa360.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de un comunicado.
 *
 * {@code seccionId} nulo = general para todo el año. ADMIN puede elegir
 * sección; SECRETARIA debe dejarlo nulo; DOCENTE debe indicar una sección
 * donde dicta clase o es tutor.
 */
public record ComunicadoCreateRequest(
        @NotNull(message = "El año escolar es obligatorio")
        Long anioId,

        Long seccionId,

        @NotBlank(message = "El contenido es obligatorio")
        @Size(max = 5000, message = "El contenido no puede superar los 5000 caracteres")
        String contenido
) {
}
