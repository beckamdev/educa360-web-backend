package com.educa360.backend.dto;

import com.educa360.backend.entity.PlataformaDispositivo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro de un token de push.
 *
 * El token lo genera la app móvil (Expo); el backend sólo lo guarda.
 * {@code plataforma} es informativa y se valida contra el enum.
 */
public record DispositivoRequest(
        @NotBlank(message = "El token del dispositivo es obligatorio")
        @Size(max = 255, message = "El token no puede superar los 255 caracteres")
        String token,

        @NotNull(message = "La plataforma es obligatoria")
        PlataformaDispositivo plataforma
) {
}
