package com.educa360.backend.dto;

/**
 * Resultado de buscar un apoderado por DNI.
 *
 * Devuelve 200 siempre: {@code existe=false} es una respuesta de negocio
 * (el frontend debe mostrar el formulario del apoderado), no un error.
 */
public record ApoderadoBusquedaResponse(
        boolean existe,
        ApoderadoResponse apoderado
) {
}
