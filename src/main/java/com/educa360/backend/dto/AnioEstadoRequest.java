package com.educa360.backend.dto;

import com.educa360.backend.entity.AnioEscolar;
import jakarta.validation.constraints.NotNull;

/** Abrir / cerrar / planificar un año: {"estado": "PLANIFICADO"|"ABIERTO"|"CERRADO"}. */
public record AnioEstadoRequest(
        @NotNull(message = "El estado (PLANIFICADO/ABIERTO/CERRADO) es obligatorio")
        AnioEscolar.Estado estado
) {
}
