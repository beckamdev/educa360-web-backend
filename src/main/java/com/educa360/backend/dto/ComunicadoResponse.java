package com.educa360.backend.dto;

import com.educa360.backend.entity.Comunicado;

import java.time.LocalDateTime;

/** Comunicado tal como lo consumen los paneles y las familias. */
public record ComunicadoResponse(
        Long id,
        Long autorId,
        String autorNombre,
        Long anioId,
        String anioNombre,
        Long seccionId,
        String seccionEtiqueta,
        String contenido,
        boolean activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ComunicadoResponse desde(Comunicado comunicado) {
        var autor = comunicado.getAutor();
        var seccion = comunicado.getSeccion();
        return new ComunicadoResponse(
                comunicado.getId(),
                autor.getId(),
                autor.getNombres() + " " + autor.getApellidos(),
                comunicado.getAnio().getId(),
                comunicado.getAnio().getNombre(),
                seccion != null ? seccion.getId() : null,
                seccion != null ? seccion.getEtiqueta() : null,
                comunicado.getContenido(),
                comunicado.isActivo(),
                comunicado.getCreatedAt(),
                comunicado.getUpdatedAt()
        );
    }
}
