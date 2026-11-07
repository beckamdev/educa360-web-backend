package com.educa360.backend.dto;

import com.educa360.backend.entity.Notificacion;
import com.educa360.backend.entity.TipoNotificacion;

import java.time.LocalDateTime;

/**
 * Payload REST y también payload del push STOMP.
 *
 * No expone la entidad ni el usuario destinatario: el cliente ya sabe quién
 * es y no debe recibir datos de otras cuentas.
 */
public record NotificacionResponse(
        Long id,
        TipoNotificacion tipo,
        String titulo,
        String mensaje,
        Long referenciaId,
        boolean leida,
        LocalDateTime leidaAt,
        LocalDateTime createdAt
) {

    public static NotificacionResponse desde(Notificacion notificacion) {
        return new NotificacionResponse(
                notificacion.getId(),
                notificacion.getTipo(),
                notificacion.getTitulo(),
                notificacion.getMensaje(),
                notificacion.getReferenciaId(),
                notificacion.isLeida(),
                notificacion.getLeidaAt(),
                notificacion.getCreatedAt());
    }
}
