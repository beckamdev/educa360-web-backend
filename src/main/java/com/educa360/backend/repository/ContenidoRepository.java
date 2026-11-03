package com.educa360.backend.repository;

import com.educa360.backend.entity.Contenido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContenidoRepository extends JpaRepository<Contenido, Long> {

    List<Contenido> findByUnidadIdOrderByOrdenAsc(Long unidadId);

    /** Último orden de la unidad, para agregar el siguiente contenido al final. */
    Optional<Contenido> findFirstByUnidadIdOrderByOrdenDesc(Long unidadId);
}
