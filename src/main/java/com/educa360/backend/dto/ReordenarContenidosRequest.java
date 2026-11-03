package com.educa360.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Reordenamiento completo de los contenidos de una unidad.
 *
 * La lista debe traer exactamente los ids actuales, en el orden deseado.
 * El servicio hace dos pasadas dentro de la misma transacción para no
 * violar el unique (unidad_id, orden).
 */
public record ReordenarContenidosRequest(
        @NotNull(message = "La lista de contenidos es obligatoria")
        List<@NotNull(message = "Ningún contenido puede ser nulo") Long> contenidoIds
) {
}
