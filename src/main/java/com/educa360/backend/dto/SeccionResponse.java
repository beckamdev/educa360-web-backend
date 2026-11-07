package com.educa360.backend.dto;

import com.educa360.backend.entity.Seccion;

import java.time.LocalDateTime;

/**
 * Ficha de una sección para el panel: incluye el nombre del tutor y el del
 * año para poder pintar la tabla sin N peticiones.
 */
public record SeccionResponse(
        Long id,
        Integer grado,
        String letra,
        String etiqueta,   // "3°A"
        String aula,
        Long tutorId,
        String tutorNombre,
        Long anioId,
        String anioNombre,
        LocalDateTime createdAt
) {
    public static SeccionResponse desde(Seccion seccion) {
        return new SeccionResponse(
                seccion.getId(),
                seccion.getGrado(),
                seccion.getLetra(),
                seccion.getEtiqueta(),
                seccion.getAula(),
                seccion.getTutor() != null ? seccion.getTutor().getId() : null,
                nombreDe(seccion),
                seccion.getAnio().getId(),
                seccion.getAnio().getNombre(),
                seccion.getCreatedAt()
        );
    }

    private static String nombreDe(Seccion seccion) {
        if (seccion.getTutor() == null || seccion.getTutor().getUser() == null) {
            return null;
        }
        return seccion.getTutor().getUser().getNombres() + " " + seccion.getTutor().getUser().getApellidos();
    }
}
