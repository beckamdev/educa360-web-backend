package com.educa360.backend.dto;

import com.educa360.backend.entity.Matricula;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Matrícula con su sección y año — la ficha que Secretaría confirma tras inscribir. */
public record MatriculaResponse(
        Long id,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        Long seccionId,
        String seccionEtiqueta, // "3°A"
        Long anioId,
        String anioNombre,
        LocalDate fechaMatricula,
        Matricula.SituacionFinal situacionFinal,
        String observacion,
        LocalDateTime createdAt
) {
    public static MatriculaResponse desde(Matricula matricula) {
        return new MatriculaResponse(
                matricula.getId(),
                matricula.getEstudiante().getId(),
                matricula.getEstudiante().getUser().getNombres() + " "
                        + matricula.getEstudiante().getUser().getApellidos(),
                matricula.getEstudiante().getUser().getDni(),
                matricula.getSeccion().getId(),
                matricula.getSeccion().getEtiqueta(),
                matricula.getAnio().getId(),
                matricula.getAnio().getNombre(),
                matricula.getFechaMatricula(),
                matricula.getSituacionFinal(),
                matricula.getObservacion(),
                matricula.getCreatedAt()
        );
    }
}
