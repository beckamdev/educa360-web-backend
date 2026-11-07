package com.educa360.backend.repository;

import com.educa360.backend.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);

    List<Notificacion> findByUsuarioIdAndLeidaOrderByCreatedAtDesc(Long usuarioId, boolean leida);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    /** Busca por id y dueño en una sola consulta: lo ajeno responde 404. */
    Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Notificacion n
               set n.leida = true,
                   n.leidaAt = :ahora
             where n.usuario.id = :usuarioId
               and n.leida = false
            """)
    int marcarTodasLeidas(@Param("usuarioId") Long usuarioId,
                          @Param("ahora") LocalDateTime ahora);
}
