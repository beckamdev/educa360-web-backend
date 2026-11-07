package com.educa360.backend.repository;

import com.educa360.backend.entity.Bimestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BimestreRepository extends JpaRepository<Bimestre, Long> {

    List<Bimestre> findByAnioIdOrderByNumeroAsc(Long anioId);

    boolean existsByAnioIdAndNumero(Long anioId, Integer numero);

    boolean existsByAnioIdAndNumeroAndIdNot(Long anioId, Integer numero, Long id);

    /** ¿El año tiene algún bimestre abierto? (bloquea el cierre del año) */
    boolean existsByAnioIdAndEstado(Long anioId, Bimestre.Estado estado);

    /** ¿Hay otro bimestre abierto además de 'id'? (regla: sólo uno vigente) */
    boolean existsByEstadoAndIdNot(Bimestre.Estado estado, Long id);

    /** El bimestre vigente: sólo puede haber uno ABIERTO, por eso Optional. */
    Optional<Bimestre> findByEstado(Bimestre.Estado estado);
}
