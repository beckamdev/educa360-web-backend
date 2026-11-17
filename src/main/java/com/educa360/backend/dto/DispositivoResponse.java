package com.educa360.backend.dto;

import com.educa360.backend.entity.Dispositivo;
import com.educa360.backend.entity.PlataformaDispositivo;

import java.time.LocalDateTime;

/**
 * Vista del dispositivo registrado. Incluye el token porque el cliente
 * necesita saber cuál está dado de alta para poder borrarlo al cerrar sesión.
 */
public record DispositivoResponse(
        Long id,
        String token,
        PlataformaDispositivo plataforma,
        LocalDateTime createdAt
) {
    public static DispositivoResponse desde(Dispositivo dispositivo) {
        return new DispositivoResponse(
                dispositivo.getId(),
                dispositivo.getToken(),
                dispositivo.getPlataforma(),
                dispositivo.getCreatedAt()
        );
    }
}
