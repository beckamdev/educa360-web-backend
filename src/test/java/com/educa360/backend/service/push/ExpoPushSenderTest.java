package com.educa360.backend.service.push;

import com.educa360.backend.dto.NotificacionResponse;
import com.educa360.backend.entity.TipoNotificacion;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del cliente HTTP de Expo contra un servidor local.
 *
 * No tocan Internet: reproducen las respuestas reales del servicio
 * ({@code status: ok}, {@code DeviceNotRegistered}, error HTTP) para fijar
 * el contrato sin depender de credenciales ni de la red.
 */
class ExpoPushSenderTest {

    private HttpServer servidor;
    private String url;

    private final AtomicReference<String> respuesta = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final AtomicReference<String> autorizacion = new AtomicReference<>();

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            byte[] salida = respuesta.get().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(status.get(), salida.length);
            intercambio.getResponseBody().write(salida);
            intercambio.close();
        });
        servidor.start();
        url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/push";
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("Un lote ok devuelve ENVIADO y manda el payload de la notificación")
    void loteOk() {
        respuesta.set("""
                {"data":[{"status":"ok","id":"1"},{"status":"ok","id":"2"}]}""");
        ExpoPushSender sender = new ExpoPushSender(url, "");

        List<PushSender.Resultado> resultados = sender.enviar(List.of(
                mensaje("ExponentPushToken[aaa]"),
                mensaje("ExponentPushToken[bbb]")));

        assertEquals(List.of(PushSender.Resultado.ENVIADO,
                PushSender.Resultado.ENVIADO), resultados);
        assertTrue(cuerpo.get().startsWith("["), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"to\":\"ExponentPushToken[aaa]\""), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"title\":\"Nueva nota\""), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"body\":\"Matematicas: 15.50\""), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"notificacionId\":99"), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"tipo\":\"CALIFICACION\""), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"referenciaId\":400"), cuerpo.get());
    }

    @Test
    @DisplayName("DeviceNotRegistered se traduce en TOKEN_INVALIDO, no en un error genérico")
    void tokenInvalido() {
        respuesta.set("""
                {"data":[
                  {"status":"ok","id":"1"},
                  {"status":"error","message":"not registered",
                   "details":{"error":"DeviceNotRegistered"}}
                ]}""");
        ExpoPushSender sender = new ExpoPushSender(url, "");

        List<PushSender.Resultado> resultados = sender.enviar(List.of(
                mensaje("ExponentPushToken[aaa]"),
                mensaje("ExponentPushToken[bbb]")));

        assertEquals(List.of(PushSender.Resultado.ENVIADO,
                PushSender.Resultado.TOKEN_INVALIDO), resultados);
    }

    @Test
    @DisplayName("Una respuesta única en objeto (no en array) también se interpreta")
    void respuestaUnicaEnObjeto() {
        respuesta.set("""
                {"data":{"status":"error","details":{"error":"DeviceNotRegistered"}}}""");
        ExpoPushSender sender = new ExpoPushSender(url, "");

        assertEquals(List.of(PushSender.Resultado.TOKEN_INVALIDO),
                sender.enviar(List.of(mensaje("ExponentPushToken[aaa]"))));
    }

    @Test
    @DisplayName("Un 500 de Expo devuelve ERROR y no lanza")
    void errorHttpNoLanza() {
        status.set(500);
        respuesta.set("""
                {"errors":[{"code":"INTERNAL_SERVER_ERROR"}]}""");
        ExpoPushSender sender = new ExpoPushSender(url, "");

        assertEquals(List.of(PushSender.Resultado.ERROR),
                sender.enviar(List.of(mensaje("ExponentPushToken[aaa]"))));
    }

    @Test
    @DisplayName("Si el proyecto Expo usa Push Security, manda el Bearer")
    void enviaAccessToken() {
        respuesta.set("""
                {"data":[{"status":"ok","id":"1"}]}""");
        ExpoPushSender sender = new ExpoPushSender(url, "secreto-de-expo");

        sender.enviar(List.of(mensaje("ExponentPushToken[aaa]")));

        assertEquals("Bearer secreto-de-expo", autorizacion.get());
    }

    @Test
    @DisplayName("Sin URL configurada devuelve ERROR sin llamar a la red")
    void urlVacia() {
        ExpoPushSender sender = new ExpoPushSender("", "");

        assertEquals(List.of(PushSender.Resultado.ERROR),
                sender.enviar(List.of(mensaje("ExponentPushToken[aaa]"))));
    }

    private PushSender.MensajePush mensaje(String token) {
        return new PushSender.MensajePush(token, new NotificacionResponse(
                99L,
                TipoNotificacion.CALIFICACION,
                "Nueva nota",
                "Matematicas: 15.50",
                400L,
                false,
                null,
                LocalDateTime.of(2026, 3, 9, 8, 0)));
    }
}
