package com.educa360.backend.repository;

import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    boolean existsByEstudianteAndSeccion(Estudiante estudiante, Seccion seccion);

    /** ¿Ya tiene el estudiante una matrícula en ESTE año? (en otra sección) */
    boolean existsByEstudianteIdAndSeccionAnioId(Long estudianteId, Long anioId);

    /** La matrícula vigente del alumno: la del año ABIERTO. */
    Optional<Matricula> findByEstudianteIdAndSeccionAnioId(Long estudianteId, Long anioId);

    /** Todas las matrículas de un año (para el listado de Secretaría). */
    List<Matricula> findBySeccionAnioId(Long anioId);
}
