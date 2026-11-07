package com.educa360.backend.repository;

import com.educa360.backend.entity.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
