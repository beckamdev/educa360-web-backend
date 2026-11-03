package com.educa360.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Alta/edición de un bimestre. El nombre se deriva del número. */
public record BimestreRequest(
        @NotNull(message = "El número del bimestre es obligatorio")
        @Min(value = 1, message = "El bimestre va de 1 a 4")
        @Max(value = 4, message = "El bimestre va de 1 a 4")
        Integer numero,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate fechaFin
) {
}
