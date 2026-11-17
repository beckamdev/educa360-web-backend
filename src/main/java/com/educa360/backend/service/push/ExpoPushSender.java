package com.educa360.backend.service.push;

import com.educa360.backend.dto.NotificacionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP del Expo Push Service.
 *
 * No usa SDK ni credenciales de Firebase/APNs: la app móvil sube esas
 * credenciales a EAS y Expo enruta cada token a FCM o APNs. El backend sólo
 * necesita el token del dispositivo. Opcionalmente puede enviar un access
 * token de Expo Push Security ({@code app.push.expo.access-token}).
 *
 * Contrato de Expo:
 * <pre>
 * POST https://exp.host/--/api/v2/push/send
 * [{"to":"ExponentPushToken[...]","title":"...","body":"...","data":{...}}]
 * {"data":[{"status":"ok","id":"..."},{"status":"error","details":{"error":"DeviceNotRegistered"}}]}
 * </pre>
 */
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final String url;
    private final String accessToken;
    private final RestClient client;

    public ExpoPushSender(String url, String accessToken) {
        this.url = url == null ? "" : url.trim();
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.client = construirCliente();
    }

    @Override
    public boolean activo() {
        return true;
    }

    @Override
    public List<Resultado> enviar(List<MensajePush> mensajes) {
        if (mensajes.isEmpty()) {
            return List.of();
        }
        if (url.isBlank()) {
            log.warn("Push habilitado pero app.push.expo.url está vacío");
            return errores(mensajes.size());
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> respuesta = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!accessToken.isBlank()) {
                            headers.setBearerAuth(accessToken);
                        }
                    })
                    .body(mensajes.stream().map(this::cuerpo).toList())
                    .retrieve()
                    .body(Map.class);

            return interpretar(respuesta != null ? respuesta.get("data") : null,
                    mensajes.size());
        } catch (RestClientResponseException ex) {
            log.warn("Expo Push respondió {}: {}", ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            return errores(mensajes.size());
        } catch (RuntimeException ex) {
            // Un fallo de push nunca debe romper el evento ya confirmado.
            log.warn("No se pudo enviar el push a Expo: {}", ex.getMessage());
            return errores(mensajes.size());
        }
    }

    private RestClient construirCliente() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory).build();
    }

    private Map<String, Object> cuerpo(MensajePush mensaje) {
        NotificacionResponse notificacion = mensaje.notificacion();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notificacionId", notificacion.id());
        if (notificacion.tipo() != null) {
            data.put("tipo", notificacion.tipo().name());
        }
        if (notificacion.referenciaId() != null) {
            data.put("referenciaId", notificacion.referenciaId());
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("to", mensaje.token());
        cuerpo.put("title", notificacion.titulo());
        cuerpo.put("body", notificacion.mensaje());
        cuerpo.put("sound", "default");
        cuerpo.put("priority", "high");
        cuerpo.put("data", data);
        return cuerpo;
    }

    private List<Resultado> interpretar(Object data, int esperados) {
        List<Resultado> resultados = errores(esperados);
        if (data instanceof List<?> lista) {
            for (int i = 0; i < Math.min(lista.size(), esperados); i++) {
                if (lista.get(i) instanceof Map<?, ?> mensaje) {
                    resultados.set(i, interpretarMensaje(mensaje));
                }
            }
        } else if (data instanceof Map<?, ?> mensaje && esperados == 1) {
            resultados.set(0, interpretarMensaje(mensaje));
        } else {
            log.warn("Expo Push devolvió una respuesta inesperada");
        }
        return resultados;
    }

    private Resultado interpretarMensaje(Map<?, ?> mensaje) {
        if ("ok".equals(mensaje.get("status"))) {
            return Resultado.ENVIADO;
        }
        Object details = mensaje.get("details");
        if (details instanceof Map<?, ?> detalle
                && "DeviceNotRegistered".equals(detalle.get("error"))) {
            return Resultado.TOKEN_INVALIDO;
        }
        log.warn("Expo Push rechazó un envío: {}", mensaje.get("message"));
        return Resultado.ERROR;
    }

    private List<Resultado> errores(int cantidad) {
        return new ArrayList<>(Collections.nCopies(cantidad, Resultado.ERROR));
    }
}
