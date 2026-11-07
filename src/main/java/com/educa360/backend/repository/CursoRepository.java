package com.educa360.backend.repository;

import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Seccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CursoRepository extends JpaRepository<Curso, Long> {

    /** Una materia sólo puede tener UN curso por sección (nadie más da Matemáticas en 3°A). */
    boolean existsByMateriaAndSeccion(Materia materia, Seccion seccion);

    List<Curso> findBySeccionId(Long seccionId);

    /** Cursos de todas las secciones de un año (audiencia de comunicados generales). */
    List<Curso> findBySeccionAnioId(Long anioId);

    List<Curso> findByDocenteId(Long docenteId);
}
