package com.educa360.backend.dto;

import com.educa360.backend.entity.TicketJustificacion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Resolución de un ticket por Secretaría.
 *
 * Sólo se admite APROBADO o RECHAZADO; el servicio rechaza PENDIENTE. La
 * fecha de resolución y la secretaria salen del servidor y del token.
 */
public record TicketResolverRequest(
        @NotNull(message = "El estado de resolución es obligatorio")
        TicketJustificacion.Estado estado,

        @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres")
        String observacionesAdmin
) {
}
