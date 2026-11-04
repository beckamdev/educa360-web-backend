package com.educa360.backend.repository;

import com.educa360.backend.entity.Comunicado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ComunicadoRepository extends JpaRepository<Comunicado, Long> {

    // ==================== ADMIN ====================

    List<Comunicado> findByActivoTrueOrderByCreatedAtDesc();

    List<Comunicado> findByAnioIdAndActivoTrueOrderByCreatedAtDesc(Long anioId);

    List<Comunicado> findBySeccionIdAndActivoTrueOrderByCreatedAtDesc(Long seccionId);

    List<Comunicado> findByAnioIdAndSeccionIdAndActivoTrueOrderByCreatedAtDesc(
            Long anioId, Long seccionId);

    // ==================== SECRETARÍA ====================

    List<Comunicado> findBySeccionIsNullAndActivoTrueOrderByCreatedAtDesc();

    List<Comunicado> findByAnioIdAndSeccionIsNullAndActivoTrueOrderByCreatedAtDesc(Long anioId);

    // ==================== Alumno, apoderado y docente ====================

    /**
     * Comunicados visibles: generales del año y los dirigidos a una de las
     * secciones indicadas. El llamador garantiza listas no vacías.
     */
    @Query("""
            select c from Comunicado c
            where c.activo = true
              and c.anio.id in :anios
              and (c.seccion is null or c.seccion.id in :secciones)
            order by c.createdAt desc
            """)
    List<Comunicado> findVisibles(@Param("anios") Collection<Long> anios,
                                  @Param("secciones") Collection<Long> secciones);
}
