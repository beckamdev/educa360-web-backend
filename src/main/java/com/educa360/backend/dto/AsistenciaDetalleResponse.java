package com.educa360.backend.dto;

import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.DiaSemana;

import java.time.LocalDate;
import java.time.LocalTime;

/** Una asistencia dentro del historial de un alumno. */
public record AsistenciaDetalleResponse(
        Long id,
        LocalDate fecha,
        Long cursoId,
        String materiaNombre,
        Long bloqueId,
        DiaSemana dia,
        LocalTime horaInicio,
        LocalTime horaFin,
        Asistencia.Estado estado,
        String observacion,
        Long ticketId
) {
    public static AsistenciaDetalleResponse desde(Asistencia asistencia) {
        return new AsistenciaDetalleResponse(
                asistencia.getId(),
                asistencia.getFecha(),
                asistencia.getBloque().getCurso().getId(),
                asistencia.getBloque().getCurso().getMateria().getNombre(),
                asistencia.getBloque().getId(),
                asistencia.getBloque().getDia(),
                asistencia.getBloque().getHoraInicio(),
                asistencia.getBloque().getHoraFin(),
                asistencia.getEstado(),
                asistencia.getObservacion(),
                asistencia.getTicket() != null ? asistencia.getTicket().getId() : null
        );
    }
}
