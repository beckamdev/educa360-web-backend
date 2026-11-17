package com.educa360.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * El usuario autenticado reemplaza SUS teléfonos.
 *
 * Semántica de reemplazo: la lista enviada es la que queda. Una lista vacía
 * quita todos los números. No se acepta {@code null} (a diferencia de la
 * edición de ADMIN, donde {@code null} significa "no tocar"): aquí el cliente
 * siempre manda el estado final.
 */
public record TelefonosUpdateRequest(
        @NotNull(message = "La lista de teléfonos es obligatoria (usa [] para quitarlos)")
        @Size(max = 5, message = "No se pueden registrar más de 5 teléfonos")
        List<@NotBlank(message = "El teléfono no puede estar vacío")
        @Pattern(regexp = "\\+?\\d{7,15}",
                message = "Teléfono inválido: sólo dígitos, opcionalmente con +") String> telefonos
) {
    public TelefonosUpdateRequest {
        if (telefonos != null) {
            telefonos = telefonos.stream()
                    .map(numero -> numero == null ? null : numero.trim())
                    .toList();
        }
    }
}
