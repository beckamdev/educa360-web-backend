package com.educa360.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Una fila de la hoja de asistencia.
 *
 * El docente no elige TARDANZA ni JUSTIFICADA: sólo dice si el alumno asistió.
 * El servicio deriva PRESENTE/TARDANZA según la hora de registro.
 */
public record AsistenciaItemRequest(
        @NotNull(message = "La matrícula es obligatoria")
        Long matriculaId,

        @NotNull(message = "Debes indicar si el alumno asistió")
        Boolean asistio,

        String observacion
) {
}
