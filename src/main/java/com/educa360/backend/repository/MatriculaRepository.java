package com.educa360.backend.repository;

import com.educa360.backend.entity.Matricula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    /** ¿Ya tiene el estudiante una matrícula en ESTE año? */
    boolean existsByEstudianteIdAndAnioId(Long estudianteId, Long anioId);

    /** La matrícula anual del alumno (una sola por año). */
    Optional<Matricula> findByEstudianteIdAndAnioId(Long estudianteId, Long anioId);

    /** Todas las matrículas de un año (listado de Secretaría). */
    List<Matricula> findByAnioId(Long anioId);

    /** Matrículas de una sección concreta. */
    List<Matricula> findBySeccionId(Long seccionId);
}
