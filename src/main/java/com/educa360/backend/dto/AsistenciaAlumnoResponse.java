package com.educa360.backend.dto;

import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.Matricula;

/**
 * Una fila de la hoja de clase.
 *
 * Si el alumno todavía no tiene asistencia registrada, {@code asistenciaId},
 * {@code estado} y {@code observacion} llegan en null: el docente ve la clase
 * completa y decide a quién marcar.
 */
public record AsistenciaAlumnoResponse(
        Long asistenciaId,
        Long matriculaId,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        Asistencia.Estado estado,
        String observacion
) {
    public static AsistenciaAlumnoResponse desde(Matricula matricula, Asistencia asistencia) {
        return new AsistenciaAlumnoResponse(
                asistencia != null ? asistencia.getId() : null,
                matricula.getId(),
                matricula.getEstudiante().getId(),
                matricula.getEstudiante().getUser().getNombres() + " "
                        + matricula.getEstudiante().getUser().getApellidos(),
                matricula.getEstudiante().getUser().getDni(),
                asistencia != null ? asistencia.getEstado() : null,
                asistencia != null ? asistencia.getObservacion() : null
        );
    }
}
