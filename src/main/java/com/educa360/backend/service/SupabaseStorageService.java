package com.educa360.backend.service;

import com.educa360.backend.exception.StorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente mínimo de Supabase Storage usando su API REST.
 *
 * La service role key sólo vive en el backend: nunca se envía al SPA. El
 * backend sube, firma URLs de descarga y elimina objetos de los buckets
 * privados {@code materiales} y {@code sustentos}.
 *
 * Si {@code app.supabase.url} o {@code app.supabase.service-role-key} están
 * vacíos, los endpoints de archivos responden 503 en vez de fallar con 500.
 */
@Service
public class SupabaseStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final String baseUrl;
    private final String serviceRoleKey;

    public SupabaseStorageService(
            @Value("${app.supabase.url:}") String baseUrl,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.serviceRoleKey = serviceRoleKey == null ? "" : serviceRoleKey.trim();
    }

    @Override
    public void upload(String bucket, String path, byte[] contenido, String contentType) {
        try {
            client().post()
                    .uri(objectUri(bucket, path))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("x-upsert", "false")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(contenido)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new StorageUnavailableException(
                    "No se pudo subir el archivo a Storage: " + ex.getStatusCode(), ex);
        } catch (RuntimeException ex) {
            throw new StorageUnavailableException("No se pudo subir el archivo a Storage", ex);
        }
    }

    @Override
    public String signedUrl(String bucket, String path, Duration ttl) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> respuesta = client().post()
                    .uri(signUri(bucket, path))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", Math.max(1, ttl.getSeconds())))
                    .retrieve()
                    .body(Map.class);

            Object firmada = respuesta != null ? respuesta.get("signedURL") : null;
            if (firmada == null || firmada.toString().isBlank()) {
                throw new StorageUnavailableException(
                        "Storage no devolvió una URL firmada");
            }
            return urlFirmada(firmada.toString());
        } catch (RestClientResponseException ex) {
            throw new StorageUnavailableException(
                    "No se pudo firmar la descarga: " + ex.getStatusCode(), ex);
        } catch (StorageUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new StorageUnavailableException("No se pudo firmar la descarga", ex);
        }
    }

    @Override
    public void delete(String bucket, String path) {
        try {
            client().delete()
                    .uri(objectUri(bucket, path))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.warn("Storage no pudo eliminar {}/{}: {}", bucket, path, ex.getStatusCode());
        } catch (RuntimeException ex) {
            log.warn("Storage no pudo eliminar {}/{}", bucket, path, ex);
        }
    }

    /**
     * Normaliza la URL firmada que devuelve Supabase.
     *
     * Supabase responde con una ruta RELATIVA que ya incluye
     * {@code /object/sign/...} pero NO el prefijo {@code /storage/v1}:
     *
     * <pre>{"signedURL":"/object/sign/materiales/1/guia.pdf?token=..."}</pre>
     *
     * Concatenarla directamente a {@code baseUrl} producía
     * {@code https://proyecto.supabase.co/object/sign/...}, que Supabase
     * rechaza con 404 ("requested path is invalid"). Aquí se agrega el
     * prefijo una sola vez y se respeta una URL absoluta si ya viniera así.
     */
    private String urlFirmada(String firmada) {
        if (firmada.startsWith("http://") || firmada.startsWith("https://")) {
            return firmada;
        }
        String relativa = firmada.startsWith("/") ? firmada : "/" + firmada;
        return relativa.startsWith("/storage/v1/")
                ? baseUrl + relativa
                : baseUrl + "/storage/v1" + relativa;
    }

    private RestClient client() {
        if (baseUrl.isBlank() || serviceRoleKey.isBlank()) {
            throw new StorageUnavailableException(
                    "Supabase Storage no está configurado: define APP_SUPABASE_URL "
                            + "y APP_SUPABASE_SERVICE_ROLE_KEY");
        }
        return RestClient.create(baseUrl);
    }

    private URI objectUri(String bucket, String path) {
        return URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + path);
    }

    private URI signUri(String bucket, String path) {
        return URI.create(baseUrl + "/storage/v1/object/sign/" + bucket + "/" + path);
    }
}
