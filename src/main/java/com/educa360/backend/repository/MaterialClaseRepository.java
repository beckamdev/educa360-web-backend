package com.educa360.backend.repository;

import com.educa360.backend.entity.MaterialClase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialClaseRepository extends JpaRepository<MaterialClase, Long> {

    /** Materiales activos de un curso (docente y ADMIN, incluidos borradores). */
    List<MaterialClase> findByCursoIdAndActivoTrueOrderByCreatedAtDesc(Long cursoId);

    /** Materiales activos y publicados de un curso (alumno y apoderado). */
    List<MaterialClase> findByCursoIdAndPublicadoTrueAndActivoTrueOrderByCreatedAtDesc(Long cursoId);

    /** ¿El curso ya tiene materiales? Bloquea la baja del curso. */
    boolean existsByCursoIdAndActivoTrue(Long cursoId);

    /** ¿El contenido ya tiene materiales? Bloquea la baja del contenido. */
    boolean existsByContenidoIdAndActivoTrue(Long contenidoId);

    /** ¿La unidad tiene algún contenido con materiales? Bloquea la baja de la unidad. */
    boolean existsByContenidoUnidadIdAndActivoTrue(Long unidadId);
}
