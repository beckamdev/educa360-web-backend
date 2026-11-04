package com.educa360.backend.dto;

import jakarta.validation.constraints.NotNull;

/** Publicar o despublicar un material. */
public record MaterialPublicacionRequest(
        @NotNull(message = "El estado de publicación es obligatorio")
        Boolean publicado
) {
}
