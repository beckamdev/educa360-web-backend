package com.educa360.backend.dto;

import com.educa360.backend.entity.Curso;

import java.time.LocalDateTime;

/**
 * Ficha de un curso para el panel: Matemática · 3°A · docente · año.
 * El horario no viaja aquí: se pinta por separado (GET /admin/cursos/{id}/horario).
 */
public record CursoResponse(
        Long id,
        Long docenteId,
        String docenteNombre,
        Long materiaId,
        String materiaNombre,
        Long seccionId,
        String seccionEtiqueta, // "3°A"
        Long anioId,
        String anioNombre,
        LocalDateTime createdAt
) {
    public static CursoResponse desde(Curso curso) {
        return new CursoResponse(
                curso.getId(),
                curso.getDocente().getId(),
                curso.getDocente().getUser().getNombres() + " "
                        + curso.getDocente().getUser().getApellidos(),
                curso.getMateria().getId(),
                curso.getMateria().getNombre(),
                curso.getSeccion().getId(),
                curso.getSeccion().getEtiqueta(),
                curso.getSeccion().getAnio().getId(),
                curso.getSeccion().getAnio().getNombre(),
                curso.getCreatedAt()
        );
    }
}
