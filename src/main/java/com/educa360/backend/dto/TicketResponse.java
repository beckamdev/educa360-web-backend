package com.educa360.backend.dto;

import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.TicketJustificacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Ticket de justificación con los datos que necesitan apoderado y Secretaría.
 *
 * Cuando el ticket es de día completo, los campos del bloque vienen en
 * {@code null}. El nombre del resolutor sólo aparece una vez resuelto.
 */
public record TicketResponse(
        Long id,
        Long estudianteId,
        String estudianteNombre,
        String estudianteDni,
        Long apoderadoId,
        String apoderadoNombre,
        LocalDate fechaInasistencia,
        Long bloqueId,
        Long cursoId,
        String materiaNombre,
        DiaSemana dia,
        LocalTime horaInicio,
        LocalTime horaFin,
        String motivo,
        String storagePath,
        TicketJustificacion.Estado estado,
        LocalDateTime fechaResolucion,
        Long resueltoPorId,
        String resueltoPorNombre,
        String observacionesAdmin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TicketResponse desde(TicketJustificacion ticket) {
        var estudianteUser = ticket.getEstudiante().getUser();
        var apoderadoUser = ticket.getApoderado().getUser();
        var bloque = ticket.getBloque();
        var resolutor = ticket.getResueltoPor();

        return new TicketResponse(
                ticket.getId(),
                ticket.getEstudiante().getId(),
                estudianteUser.getNombres() + " " + estudianteUser.getApellidos(),
                estudianteUser.getDni(),
                ticket.getApoderado().getId(),
                apoderadoUser.getNombres() + " " + apoderadoUser.getApellidos(),
                ticket.getFechaInasistencia(),
                bloque != null ? bloque.getId() : null,
                bloque != null ? bloque.getCurso().getId() : null,
                bloque != null ? bloque.getCurso().getMateria().getNombre() : null,
                bloque != null ? bloque.getDia() : null,
                bloque != null ? bloque.getHoraInicio() : null,
                bloque != null ? bloque.getHoraFin() : null,
                ticket.getMotivo(),
                ticket.getStoragePath(),
                ticket.getEstado(),
                ticket.getFechaResolucion(),
                resolutor != null ? resolutor.getId() : null,
                resolutor != null ? resolutor.getUser().getNombres() + " "
                        + resolutor.getUser().getApellidos() : null,
                ticket.getObservacionesAdmin(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
