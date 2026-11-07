package com.educa360.backend.repository;

import com.educa360.backend.entity.Unidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnidadRepository extends JpaRepository<Unidad, Long> {

    /** Todas las unidades de un curso, ordenadas por bimestre y número. */
    List<Unidad> findByCursoIdOrderByBimestreNumeroAscNumeroAsc(Long cursoId);

    /** Las unidades visibles para alumnos/apoderados: sólo PUBLICADA. */
    List<Unidad> findByCursoIdAndEstadoOrderByBimestreNumeroAscNumeroAsc(
            Long cursoId, Unidad.Estado estado);

    /** Unidades de un curso dentro de un bimestre (acta). */
    List<Unidad> findByCursoIdAndBimestreIdOrderByNumeroAsc(Long cursoId, Long bimestreId);

    /** Último número del curso dentro del bimestre, para asignar el siguiente. */
    Optional<Unidad> findFirstByCursoIdAndBimestreIdOrderByNumeroDesc(
            Long cursoId, Long bimestreId);

    /** ¿El curso ya tiene planificación? Bloquea su baja (M4). */
    boolean existsByCursoId(Long cursoId);
}
