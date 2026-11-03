package com.educa360.backend.dto;

import com.educa360.backend.entity.Bimestre;
import jakarta.validation.constraints.NotNull;

/** Abrir / cerrar / planificar un bimestre. */
public record BimestreEstadoRequest(
        @NotNull(message = "El estado (PLANIFICADO/ABIERTO/CERRADO) es obligatorio")
        Bimestre.Estado estado
) {
}
