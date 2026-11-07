package com.educa360.backend.repository;

import com.educa360.backend.entity.TicketJustificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface TicketJustificacionRepository
        extends JpaRepository<TicketJustificacion, Long> {

    /** Historial de un alumno, del más reciente al más antiguo. */
    List<TicketJustificacion> findByEstudianteIdOrderByFechaInasistenciaDescCreatedAtDesc(
            Long estudianteId);

    /** Bandeja de Secretaría: todos, o filtrados por estado y/o fecha. */
    List<TicketJustificacion> findAllByOrderByCreatedAtDesc();

    List<TicketJustificacion> findByEstadoOrderByCreatedAtDesc(
            TicketJustificacion.Estado estado);

    List<TicketJustificacion> findByFechaInasistenciaOrderByCreatedAtDesc(LocalDate fecha);

    List<TicketJustificacion> findByEstadoAndFechaInasistenciaOrderByCreatedAtDesc(
            TicketJustificacion.Estado estado, LocalDate fecha);

    /** Tickets activos (PENDIENTE/APROBADO) que podrían solaparse con uno nuevo. */
    List<TicketJustificacion> findByEstudianteIdAndFechaInasistenciaAndEstadoIn(
            Long estudianteId, LocalDate fecha, Collection<TicketJustificacion.Estado> estados);

    /**
     * Ticket aprobado que cubre una asistencia concreta.
     *
     * Devuelve primero el de día completo (bloque NULL) para que prevalezca
     * sobre uno de bloque si alguna vez coexistieran.
     */
    @Query("""
            select t from TicketJustificacion t
            where t.estudiante.id = :estudianteId
              and t.fechaInasistencia = :fecha
              and t.estado = com.educa360.backend.entity.TicketJustificacion.Estado.APROBADO
              and (t.bloque is null or t.bloque.id = :bloqueId)
            order by case when t.bloque is null then 0 else 1 end
            """)
    List<TicketJustificacion> buscarCoberturaAprobada(
            @Param("estudianteId") Long estudianteId,
            @Param("fecha") LocalDate fecha,
            @Param("bloqueId") Long bloqueId);
}
