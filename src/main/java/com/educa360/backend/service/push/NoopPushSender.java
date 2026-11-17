package com.educa360.backend.service.push;

import com.educa360.backend.dto.NotificacionResponse;

import java.util.List;

/**
 * Implementación por defecto cuando {@code app.push.enabled=false}.
 *
 * No se invoca: {@link #activo()} devuelve {@code false} y
 * {@code PushNotificationService} ni siquiera consulta dispositivos.
 * Existe para que el contexto de Spring siempre tenga un {@link PushSender}.
 */
public final class NoopPushSender implements PushSender {

    @Override
    public boolean activo() {
        return false;
    }

    @Override
    public List<Resultado> enviar(List<MensajePush> mensajes) {
        return mensajes.stream().map(mensaje -> Resultado.ERROR).toList();
    }
}
