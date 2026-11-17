package com.educa360.backend.service;

import com.educa360.backend.dto.NotificacionResponse;
import com.educa360.backend.entity.Dispositivo;
import com.educa360.backend.repository.DispositivoRepository;
import com.educa360.backend.service.push.PushSender;
import com.educa360.backend.service.push.PushSender.MensajePush;
import com.educa360.backend.service.push.PushSender.Resultado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Entrega de notificaciones a dispositivos (push nativo).
 *
 * Se ejecuta en un hilo aparte para que el evento de negocio no espere a la
 * red: el afterCommit de la transacción ya confirmó los datos y el WebSocket
 * ya avisó a los conectados. Si el push falla, se registra y se sigue.
 *
 * Expo acepta hasta 100 mensajes por petición; los lotes se arman aquí para
 * que un comunicado general no genere cientos de llamadas HTTP.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    static final int LOTE_EXPO = 100;

    private final DispositivoRepository dispositivoRepository;
    private final PushSender pushSender;
    private final Executor executor;

    public PushNotificationService(DispositivoRepository dispositivoRepository,
                                   PushSender pushSender,
                                   @Qualifier("pushExecutor") Executor executor) {
        this.dispositivoRepository = dispositivoRepository;
        this.pushSender = pushSender;
        this.executor = executor;
    }

    /**
     * Encola el envío de una lista de notificaciones ya persistidas.
     * {@code usuarioIds} se alinea por índice con {@code notificaciones}.
     */
    public void enviar(List<NotificacionResponse> notificaciones, List<Long> usuarioIds) {
        if (!pushSender.activo() || notificaciones.isEmpty()) {
            return;
        }
        if (notificaciones.size() != usuarioIds.size()) {
            log.warn("Push omitido: {} notificaciones y {} destinatarios",
                    notificaciones.size(), usuarioIds.size());
            return;
        }
        List<NotificacionResponse> payloads = List.copyOf(notificaciones);
        List<Long> ids = List.copyOf(usuarioIds);
        try {
            executor.execute(() -> enviarAhora(payloads, ids));
        } catch (RuntimeException ex) {
            // Cola llena o executor apagado: la notificación ya está en BD.
            log.warn("No se pudo encolar el push: {}", ex.getMessage());
        }
    }

    /** Visible para tests: hace el trabajo real, ya fuera del hilo del request. */
    void enviarAhora(List<NotificacionResponse> notificaciones, List<Long> usuarioIds) {
        Map<Long, NotificacionResponse> porUsuario = new LinkedHashMap<>();
        for (int i = 0; i < usuarioIds.size(); i++) {
            porUsuario.putIfAbsent(usuarioIds.get(i), notificaciones.get(i));
        }

        List<MensajePush> mensajes = new ArrayList<>();
        for (Dispositivo dispositivo : dispositivoRepository.findByUsuarioIdIn(porUsuario.keySet())) {
            NotificacionResponse notificacion =
                    porUsuario.get(dispositivo.getUsuario().getId());
            if (notificacion != null) {
                mensajes.add(new MensajePush(dispositivo.getToken(), notificacion));
            }
        }
        if (mensajes.isEmpty()) {
            return;
        }

        List<String> tokensInvalidos = new ArrayList<>();
        for (int inicio = 0; inicio < mensajes.size(); inicio += LOTE_EXPO) {
            List<MensajePush> lote = mensajes.subList(
                    inicio, Math.min(inicio + LOTE_EXPO, mensajes.size()));
            List<Resultado> resultados;
            try {
                resultados = pushSender.enviar(lote);
            } catch (RuntimeException ex) {
                log.warn("Fallo inesperado del proveedor push: {}", ex.getMessage());
                continue;
            }
            for (int i = 0; i < lote.size(); i++) {
                if (i < resultados.size() && resultados.get(i) == Resultado.TOKEN_INVALIDO) {
                    tokensInvalidos.add(lote.get(i).token());
                }
            }
        }

        if (!tokensInvalidos.isEmpty()) {
            int borrados = dispositivoRepository.eliminarPorTokens(tokensInvalidos);
            log.info("Tokens de push inválidos eliminados: {}", borrados);
        }
    }
}
