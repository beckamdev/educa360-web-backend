package com.educa360.backend.dto;

import com.educa360.backend.entity.ApoderadoEstudiante;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Alta de una familia desde Secretaría: apoderado + estudiante + vínculo,
 * todo en una sola transacción.
 *
 * El apoderado puede ser uno existente ({@code apoderadoId}) o crearse con
 * {@code nuevoApoderado}. Si el DNI de {@code nuevoApoderado} ya pertenece a
 * un APODERADO, se reutiliza en vez de duplicarlo.
 */
public record FamiliaCreateRequest(
        Long apoderadoId,

        @Valid
        UsuarioBasicoRequest nuevoApoderado,

        @Valid
        @NotNull(message = "Los datos del estudiante son obligatorios")
        EstudianteNuevo estudiante,

        @NotNull(message = "El parentesco es obligatorio")
        ApoderadoEstudiante.Parentesco parentesco,

        @NotNull(message = "Debes indicar si es el responsable principal")
        Boolean responsablePrincipal
) {
    /** Datos de un estudiante nuevo: cuenta + fecha de nacimiento. */
    public record EstudianteNuevo(
            @Valid
            @NotNull(message = "Los datos de la cuenta del estudiante son obligatorios")
            UsuarioBasicoRequest usuario,

            @NotNull(message = "La fecha de nacimiento es obligatoria")
            LocalDate fechaNacimiento
    ) {
    }
}
