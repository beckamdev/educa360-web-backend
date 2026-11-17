package com.educa360.backend.repository;

import com.educa360.backend.entity.Dispositivo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByToken(String token);

    List<Dispositivo> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);

    /**
     * Dispositivos de varios usuarios en una sola consulta, con el dueño
     * cargado para no tocar proxies fuera de la transacción.
     */
    @EntityGraph(attributePaths = "usuario")
    List<Dispositivo> findByUsuarioIdIn(Collection<Long> usuarioIds);

    /** Borra sólo si el token pertenece al usuario: lo ajeno no se toca. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Dispositivo d where d.token = :token and d.usuario.id = :usuarioId")
    int eliminarDeUsuario(@Param("token") String token,
                          @Param("usuarioId") Long usuarioId);

    /**
     * Limpieza de tokens que Expo/FCM reportan como inválidos
     * ({@code DeviceNotRegistered}).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Dispositivo d where d.token in :tokens")
    int eliminarPorTokens(@Param("tokens") Collection<String> tokens);
}
