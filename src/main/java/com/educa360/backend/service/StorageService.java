package com.educa360.backend.service;

import java.time.Duration;

/**
 * Abstracción mínima sobre el almacenamiento de archivos.
 *
 * La implementación real es Supabase Storage; los tests la sustituyen por un
 * mock para no depender de la red. Las rutas son relativas al bucket y nunca
 * se exponen como URL pública.
 */
public interface StorageService {

    String BUCKET_MATERIALES = "materiales";
    String BUCKET_SUSTENTOS = "sustentos";

    void upload(String bucket, String path, byte[] contenido, String contentType);

    String signedUrl(String bucket, String path, Duration ttl);

    void delete(String bucket, String path);
}
