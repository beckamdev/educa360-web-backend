package com.educa360.backend.dto;

import java.time.LocalDateTime;

/** URL firmada de corta duración para descargar un archivo privado. */
public record ArchivoDescargaResponse(
        String url,
        LocalDateTime expiresAt
) {
}
