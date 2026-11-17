package com.educa360.backend.service;

import com.educa360.backend.dto.DispositivoRequest;
import com.educa360.backend.dto.DispositivoResponse;
import com.educa360.backend.entity.Dispositivo;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.DispositivoRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Registro de tokens de push del usuario autenticado.
 *
 * La identidad sale del token JWT: un usuario no puede registrar ni borrar
 * dispositivos de otra cuenta. El registro es un upsert por token porque la
 * app debe llamarlo en cada login (los tokens cambian).
 */
@Service
public class DispositivoService {

    private final DispositivoRepository dispositivoRepository;
    private final UserRepository userRepository;

    public DispositivoService(DispositivoRepository dispositivoRepository,
                              UserRepository userRepository) {
        this.dispositivoRepository = dispositivoRepository;
        this.userRepository = userRepository;
    }

    /**
     * Registra o actualiza el token. Si el token ya existía con otro dueño,
     * se reasigna: el aparato físico ahora pertenece a quien inicia sesión.
     */
    @Transactional
    public DispositivoResponse registrar(String email, DispositivoRequest request) {
        User usuario = usuarioDe(email);
        String token = normalizar(request.token());
        Dispositivo dispositivo = dispositivoRepository.findByToken(token)
                .orElseGet(() -> new Dispositivo(usuario, token, request.plataforma()));
        dispositivo.setUsuario(usuario);
        dispositivo.setPlataforma(request.plataforma());
        return DispositivoResponse.desde(dispositivoRepository.save(dispositivo));
    }

    @Transactional(readOnly = true)
    public List<DispositivoResponse> listar(String email) {
        User usuario = usuarioDe(email);
        return dispositivoRepository
                .findByUsuarioIdOrderByCreatedAtDesc(usuario.getId())
                .stream()
                .map(DispositivoResponse::desde)
                .toList();
    }

    /**
     * Baja idempotente al cerrar sesión. Sólo borra el token si es del
     * usuario autenticado; un token ajeno no se toca y tampoco da error.
     */
    @Transactional
    public void eliminar(String email, String token) {
        User usuario = usuarioDe(email);
        dispositivoRepository.eliminarDeUsuario(normalizar(token), usuario.getId());
    }

    private String normalizar(String token) {
        String limpio = token == null ? "" : token.trim();
        if (limpio.isEmpty()) {
            throw new BadRequestException("El token del dispositivo es obligatorio");
        }
        return limpio;
    }

    private User usuarioDe(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }
}
