package com.educa360.backend.repository;

import com.educa360.backend.entity.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {

    /** La fila única de una matrícula en una unidad y criterio. */
    Optional<Calificacion> findByMatriculaIdAndUnidadIdAndCriterio(
            Long matriculaId, Long unidadId, String criterio);

    /** Todas las notas de una unidad (hoja del docente). */
    List<Calificacion> findByUnidadId(Long unidadId);

    /** Historial anual de una matrícula, ordenado por bimestre y unidad. */
    List<Calificacion> findByMatriculaIdOrderByUnidadBimestreNumeroAscUnidadNumeroAscCriterioAsc(
            Long matriculaId);

    /** Historial de una matrícula acotado a un bimestre. */
    List<Calificacion> findByMatriculaIdAndUnidadBimestreIdOrderByUnidadNumeroAscCriterioAsc(
            Long matriculaId, Long bimestreId);

    /** Notas de un curso en un bimestre (acta). */
    List<Calificacion> findByUnidadCursoIdAndUnidadBimestreIdOrderByUnidadNumeroAscCriterioAsc(
            Long cursoId, Long bimestreId);

    /** ¿La matrícula ya tiene notas? Bloquea el cambio de sección. */
    boolean existsByMatriculaId(Long matriculaId);

    /** ¿La unidad ya tiene notas? Bloquea despublicar o eliminar. */
    boolean existsByUnidadId(Long unidadId);

    /** ¿El curso ya tiene notas? Bloquea eliminar el curso. */
    boolean existsByUnidadCursoId(Long cursoId);
}
