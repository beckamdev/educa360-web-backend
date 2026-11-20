package com.educa360.backend.exception;

import com.educa360.backend.config.ZonaHoraria;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cuerpo estándar de error de la API.
 *
 * @param fields Sólo se usa en errores de validación (campo -> mensaje)
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(LocalDateTime.now(ZonaHoraria.APP), status, error, message, path, null);
    }

    public static ApiError withFields(int status, String error, String message, String path,
                                      Map<String, String> fields) {
        return new ApiError(LocalDateTime.now(ZonaHoraria.APP), status, error, message, path, fields);
    }
}
