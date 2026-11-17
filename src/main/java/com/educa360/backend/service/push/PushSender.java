package com.educa360.backend.service.push;

import com.educa360.backend.dto.NotificacionResponse;

import java.util.List;

/**
 * Canal de entrega a dispositivos (push nativo).
 *
 * Se abstrae para que el resto del backend no sepa si el proveedor es Expo,
 * Firebase o ninguno. La implementación activa se elige por configuración:
 * sin {@code app.push.enabled=true} se usa {@link NoopPushSender} y la API
 * funciona igual, sólo que no despierta apps cerradas.
 */
public interface PushSender {

    /** {@code false} en modo degradado: no hay proveedor configurado. */
    boolean activo();

    /**
     * Envía un lote (máximo 100 mensajes, límite de Expo) en una sola
     * llamada. El resultado se alinea por índice con {@code mensajes}.
     *
     * Nunca debe lanzar: un fallo de red se traduce en {@link Resultado#ERROR}
     * para no tumbar el evento de negocio que ya se confirmó.
     */
    List<Resultado> enviar(List<MensajePush> mensajes);

    /** Un destinatario concreto y el aviso que debe recibir. */
    record MensajePush(String token, NotificacionResponse notificacion) {
    }

    enum Resultado {
        ENVIADO,
        /** El proveedor dice que el token ya no existe: hay que borrarlo. */
        TOKEN_INVALIDO,
        ERROR
    }
}
