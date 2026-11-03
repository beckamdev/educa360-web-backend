package com.educa360.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Alta/edición de un año escolar. Nace PLANIFICADO; se abre con PATCH /estado. */
public record AnioEscolarRequest(
        @NotNull(message = "El año es obligatorio")
        @Min(value = 2000, message = "El año debe ser válido")
        @Max(value = 2100, message = "El año debe ser válido")
        Integer anio,

        @NotBlank(message = "El nombre del año es obligatorio")
        String nombre,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio
) {
}
