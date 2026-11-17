package com.educa360.backend.repository;

import com.educa360.backend.entity.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    /** La fila única de una matrícula en un bloque y fecha. */
    Optional<Asistencia> findByMatriculaIdAndBloqueIdAndFecha(
            Long matriculaId, Long bloqueId, LocalDate fecha);

    /** Todas las filas de una clase (bloque + fecha), para pintar la hoja. */
    List<Asistencia> findByBloqueIdAndFecha(Long bloqueId, LocalDate fecha);

    /** Filas de un alumno en una fecha (alcance de día completo de un ticket). */
    List<Asistencia> findByMatriculaIdAndFecha(Long matriculaId, LocalDate fecha);

    /**
     * Conteo por bloque y estado para una fecha, en UNA consulta agrupada.
     * Alimenta el resumen del día del docente sin abrir cada hoja.
     */
    @Query("""
            select a.bloque.id as bloqueId, a.estado as estado, count(a) as total
            from Asistencia a
            where a.bloque.id in :bloqueIds and a.fecha = :fecha
            group by a.bloque.id, a.estado
            """)
    List<ConteoBloqueEstado> contarPorBloquesYFecha(
            @Param("bloqueIds") Collection<Long> bloqueIds,
            @Param("fecha") LocalDate fecha);

    /** Proyección del conteo agrupado por bloque y estado. */
    interface ConteoBloqueEstado {
        Long getBloqueId();

        Asistencia.Estado getEstado();

        Long getTotal();
    }

    /** Fila de un alumno en un bloque y fecha (alcance de un ticket de bloque). */
    List<Asistencia> findByMatriculaIdAndFechaAndBloqueId(
            Long matriculaId, LocalDate fecha, Long bloqueId);

    /** Historial anual de una matrícula, ordenado por fecha y hora del bloque. */
    List<Asistencia> findByMatriculaIdOrderByFechaAscBloqueHoraInicioAsc(Long matriculaId);

    /** Historial acotado a un bimestre. */
    List<Asistencia> findByMatriculaIdAndFechaBetweenOrderByFechaAscBloqueHoraInicioAsc(
            Long matriculaId, LocalDate desde, LocalDate hasta);

    /** ¿La matrícula ya tiene asistencias? Bloquea el cambio de sección. */
    boolean existsByMatriculaId(Long matriculaId);

    /** ¿El bloque ya tiene asistencias? Bloquea reemplazar el horario. */
    boolean existsByBloqueId(Long bloqueId);

    /** ¿El curso ya tiene asistencias? Bloquea reemplazar el horario y borrar el curso. */
    boolean existsByBloqueCursoId(Long cursoId);
}
