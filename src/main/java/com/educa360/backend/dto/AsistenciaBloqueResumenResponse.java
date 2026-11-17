package com.educa360.backend.dto;

import com.educa360.backend.entity.HorarioBloque;

import java.time.LocalTime;

/**
 * Avance de un bloque en el resumen del día.
 *
 * {@code totalAlumnos} es el tamaño vigente de la sección (ACTIVOS con
 * matrícula no RETIRADA); {@code registradas} son las filas de asistencia ya
 * guardadas para ese bloque y fecha. {@code pendientes} es la diferencia: lo
 * que al docente le falta por marcar para cerrar la clase.
 */
public record AsistenciaBloqueResumenResponse(
        Long bloqueId,
        Long cursoId,
        String materiaNombre,
        String seccionEtiqueta,
        LocalTime horaInicio,
        LocalTime horaFin,
        int totalAlumnos,
        int registradas,
        int pendientes,
        int presentes,
        int tardanzas,
        int justificadas,
        int faltas
) {
    public static AsistenciaBloqueResumenResponse desde(HorarioBloque bloque,
                                                        int totalAlumnos,
                                                        int presentes,
                                                        int tardanzas,
                                                        int justificadas,
                                                        int faltas) {
        int registradas = presentes + tardanzas + justificadas + faltas;
        return new AsistenciaBloqueResumenResponse(
                bloque.getId(),
                bloque.getCurso().getId(),
                bloque.getCurso().getMateria().getNombre(),
                bloque.getCurso().getSeccion().getEtiqueta(),
                bloque.getHoraInicio(),
                bloque.getHoraFin(),
                totalAlumnos,
                registradas,
                Math.max(totalAlumnos - registradas, 0),
                presentes,
                tardanzas,
                justificadas,
                faltas
        );
    }
}
