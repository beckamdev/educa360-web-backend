package com.educa360.backend.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de la URL firmada de Supabase Storage.
 *
 * Supabase devuelve {@code {"signedURL":"/object/sign/..."}} SIN el prefijo
 * {@code /storage/v1}. Este test reproduce esa respuesta real (servidor HTTP
 * local) para que no vuelva el bug de la URL incompleta, que el mock de los
 * tests de API no detectaba porque devolvía una URL absoluta.
 */
class SupabaseStorageServiceTest {

    private HttpServer servidor;
    private String baseUrl;
    private final AtomicReference<String> respuesta = new AtomicReference<>();
    private final AtomicReference<String> ultimaRuta = new AtomicReference<>();

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            ultimaRuta.set(intercambio.getRequestURI().toString());
            intercambio.getRequestBody().readAllBytes();
            byte[] cuerpo = respuesta.get().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
        baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("Agrega /storage/v1 a la ruta relativa que devuelve Supabase")
    void prefijaStorageV1EnRutaRelativa() {
        respuesta.set("""
                {"signedURL":"/object/sign/materiales/1/guia.pdf?token=abc"}""");
        SupabaseStorageService servicio = new SupabaseStorageService(baseUrl, "service-role");

        String url = servicio.signedUrl("materiales", "1/guia.pdf", Duration.ofMinutes(5));

        assertEquals(baseUrl + "/storage/v1/object/sign/materiales/1/guia.pdf?token=abc", url);
        assertEquals("/storage/v1/object/sign/materiales/1/guia.pdf",
                ultimaRuta.get(), "el POST de firma también usa /storage/v1");
    }

    @Test
    @DisplayName("No duplica /storage/v1 si la respuesta ya lo trae")
    void noDuplicaStorageV1() {
        respuesta.set("""
                {"signedURL":"/storage/v1/object/sign/sustentos/2/certificado.pdf?token=xyz"}""");
        SupabaseStorageService servicio = new SupabaseStorageService(baseUrl, "service-role");

        String url = servicio.signedUrl("sustentos", "2/certificado.pdf", Duration.ofMinutes(5));

        assertEquals(baseUrl + "/storage/v1/object/sign/sustentos/2/certificado.pdf?token=xyz", url);
    }

    @Test
    @DisplayName("Respeta una URL absoluta si Supabase la devolviera así")
    void respetaUrlAbsoluta() {
        respuesta.set("""
                {"signedURL":"https://cdn.example.com/archivo.pdf?token=abc"}""");
        SupabaseStorageService servicio = new SupabaseStorageService(baseUrl, "service-role");

        String url = servicio.signedUrl("materiales", "1/guia.pdf", Duration.ofMinutes(5));

        assertEquals("https://cdn.example.com/archivo.pdf?token=abc", url);
    }
}
