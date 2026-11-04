package com.educa360.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Alta de un ticket de justificación desde la zona del apoderado.
 *
 * El alumno sale de la ruta y el apoderado del token: el request no puede
 * elegir por quién se justifica. Sin {@code bloqueId}, el ticket cubre todo
 * el día.
 */
public record TicketCreateRequest(
        @NotNull(message = "La fecha de inasistencia es obligatoria")
        LocalDate fechaInasistencia,

        Long bloqueId,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo,

        @Size(max = 500, message = "La URL del sustento no puede superar los 500 caracteres")
        String urlArchivoSustento
) {
}
