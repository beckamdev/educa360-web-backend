package com.educa360.backend.dto;

import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.HorarioBloque;

import java.time.LocalTime;

/**
 * Un bloque del horario de una SECCIÓN, con el curso al que pertenece.
 *
 * A diferencia de {@link HorarioBloqueResponse} (una hoja de un solo curso),
 * aquí viajan materia y docente para que el panel de la sección pinte la
 * grilla completa en una sola petición. {@code cursoId} permite abrir el
 * editor del curso con el PUT existente.
 */
public record HorarioSeccionResponse(
        Long bloqueId,
        Long cursoId,
        Long materiaId,
        String materiaNombre,
        Long docenteId,
        String docenteNombre,
        DiaSemana dia,
        LocalTime horaInicio,
        LocalTime horaFin
) {
    public static HorarioSeccionResponse desde(HorarioBloque bloque) {
        Curso curso = bloque.getCurso();
        return new HorarioSeccionResponse(
                bloque.getId(),
                curso.getId(),
                curso.getMateria().getId(),
                curso.getMateria().getNombre(),
                curso.getDocente().getId(),
                curso.getDocente().getUser().getNombres() + " "
                        + curso.getDocente().getUser().getApellidos(),
                bloque.getDia(),
                bloque.getHoraInicio(),
                bloque.getHoraFin()
        );
    }
}
