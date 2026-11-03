package com.educa360.backend.repository;

import com.educa360.backend.entity.AnioEscolar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnioEscolarRepository extends JpaRepository<AnioEscolar, Long> {

    boolean existsByAnio(Integer anio);

    boolean existsByNombre(String nombre);

    boolean existsByAnioAndIdNot(Integer anio, Long id);

    boolean existsByNombreAndIdNot(String nombre, Long id);

    /** ¿Hay otro año abierto además de 'id'? (regla: sólo uno vigente) */
    boolean existsByEstadoAndIdNot(AnioEscolar.Estado estado, Long id);

    /** El año vigente: sólo puede haber uno ABIERTO, por eso Optional. */
    Optional<AnioEscolar> findByEstado(AnioEscolar.Estado estado);
}
