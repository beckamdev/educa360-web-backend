package com.educa360.backend.dto;

import com.educa360.backend.entity.MaterialClase;

import java.time.LocalDateTime;

/**
 * Material de clase tal como lo consumen docente, ADMIN, alumno y apoderado.
 *
 * No expone el {@code storagePath}: la descarga siempre pasa por el endpoint
 * que firma una URL temporal.
 */
public record MaterialResponse(
        Long id,
        Long cursoId,
        String materiaNombre,
        String seccionEtiqueta,
        Long contenidoId,
        String contenidoTitulo,
        String titulo,
        String descripcion,
        String nombreOriginal,
        String mimeType,
        Long tamanoBytes,
        boolean publicado,
        LocalDateTime fechaPublicacion,
        boolean activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MaterialResponse desde(MaterialClase material) {
        var contenido = material.getContenido();
        return new MaterialResponse(
                material.getId(),
                material.getCurso().getId(),
                material.getCurso().getMateria().getNombre(),
                material.getCurso().getSeccion().getEtiqueta(),
                contenido != null ? contenido.getId() : null,
                contenido != null ? contenido.getTitulo() : null,
                material.getTitulo(),
                material.getDescripcion(),
                material.getNombreOriginal(),
                material.getMimeType(),
                material.getTamanoBytes(),
                material.isPublicado(),
                material.getFechaPublicacion(),
                material.isActivo(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
