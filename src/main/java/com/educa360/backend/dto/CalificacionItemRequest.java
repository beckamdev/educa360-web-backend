package com.educa360.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Una nota de la hoja de una unidad.
 *
 * El criterio es texto libre ("Examen parcial", "Práctica 1"). La nota es
 * vigesimal, con un máximo de dos decimales.
 */
public record CalificacionItemRequest(
        @NotNull(message = "La matrícula es obligatoria")
        Long matriculaId,

        @NotBlank(message = "El criterio es obligatorio")
        @Size(max = 120, message = "El criterio no puede superar los 120 caracteres")
        String criterio,

        @NotNull(message = "La nota es obligatoria")
        @DecimalMin(value = "0.00", message = "La nota mínima es 0.00")
        @DecimalMax(value = "20.00", message = "La nota máxima es 20.00")
        @Digits(integer = 2, fraction = 2,
                message = "La nota admite como máximo dos decimales")
        BigDecimal nota
) {
}
