package com.educa360.backend.repository;

import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    /**
     * Alumnos vigentes por sección: ACTIVOS y con matrícula no RETIRADA.
     * Una sola consulta agrupada para todos los cursos de un listado.
     */
    @Query("""
            select m.seccion.id as seccionId, count(m) as total
            from Matricula m
            where m.seccion.id in :seccionIds
              and m.situacionFinal <> :retirado
              and m.estudiante.estado = :activo
            group by m.seccion.id
            """)
    List<ConteoSeccion> contarAlumnosPorSeccion(@Param("seccionIds") Collection<Long> seccionIds,
                                                @Param("retirado") Matricula.SituacionFinal retirado,
                                                @Param("activo") Estudiante.EstadoEstudiante activo);

    /** Proyección del conteo agrupado. */
    interface ConteoSeccion {
        Long getSeccionId();

        Long getTotal();
    }

    /** ¿Ya tiene el estudiante una matrícula en ESTE año? */
    boolean existsByEstudianteIdAndAnioId(Long estudianteId, Long anioId);

    /** La matrícula anual del alumno (una sola por año). */
    Optional<Matricula> findByEstudianteIdAndAnioId(Long estudianteId, Long anioId);

    /** La matrícula del alumno en un año calendario (para tickets por fecha). */
    Optional<Matricula> findByEstudianteIdAndAnioAnio(Long estudianteId, Integer anio);

    /** Todas las matrículas de un año (listado de Secretaría). */
    List<Matricula> findByAnioId(Long anioId);

    /** Matrículas de una sección concreta. */
    List<Matricula> findBySeccionId(Long seccionId);

    /** Historial completo del alumno (todas sus matrículas). */
    List<Matricula> findByEstudianteId(Long estudianteId);
}
