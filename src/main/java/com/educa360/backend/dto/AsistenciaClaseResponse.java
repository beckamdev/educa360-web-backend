package com.educa360.backend.dto;

import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.HorarioBloque;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Hoja completa de una clase: curso, bloque, fecha y todos los alumnos. */
public record AsistenciaClaseResponse(
        Long cursoId,
        String materiaNombre,
        String seccionEtiqueta,
        Long bloqueId,
        DiaSemana dia,
        LocalTime horaInicio,
        LocalTime horaFin,
        LocalDate fecha,
        List<AsistenciaAlumnoResponse> asistencias
) {
    public static AsistenciaClaseResponse desde(Curso curso, HorarioBloque bloque,
                                                LocalDate fecha,
                                                List<AsistenciaAlumnoResponse> asistencias) {
        return new AsistenciaClaseResponse(
                curso.getId(),
                curso.getMateria().getNombre(),
                curso.getSeccion().getEtiqueta(),
                bloque.getId(),
                bloque.getDia(),
                bloque.getHoraInicio(),
                bloque.getHoraFin(),
                fecha,
                asistencias
        );
    }
}
