package com.educa360.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Alta/edición de una sección ("3°A") dentro de un año escolar.
 * grado 1..5 porque el sistema es SÓLO secundaria.
 */
public record SeccionRequest(
        @NotNull(message = "El grado es obligatorio")
        @Min(value = 1, message = "El grado va de 1 a 5 (secundaria)")
        @Max(value = 5, message = "El grado va de 1 a 5 (secundaria)")
        Integer grado,

        @NotBlank(message = "La letra es obligatoria")
        @Pattern(regexp = "[A-Z]", message = "La letra debe ser una mayúscula (A, B, C...)")
        String letra,

        String aula, // texto libre, opcional

        Long tutorId, // docente a cargo; opcional

        @NotNull(message = "El año de la sección es obligatorio")
        Long anioId
) {
}
