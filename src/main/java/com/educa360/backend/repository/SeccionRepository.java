package com.educa360.backend.repository;

import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Seccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeccionRepository extends JpaRepository<Seccion, Long> {

    /** "3°A" sólo puede existir una vez dentro de un año. */
    boolean existsByGradoAndLetraAndAnio(Integer grado, String letra, AnioEscolar anio);

    List<Seccion> findByAnioId(Long anioId);
}
