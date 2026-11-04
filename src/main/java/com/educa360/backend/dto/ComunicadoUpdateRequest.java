package com.educa360.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edición de un comunicado. El año no se mueve: si cambia la sección, debe
 * pertenecer al mismo año del comunicado.
 */
public record ComunicadoUpdateRequest(
        Long seccionId,

        @NotBlank(message = "El contenido es obligatorio")
        @Size(max = 5000, message = "El contenido no puede superar los 5000 caracteres")
        String contenido
) {
}
