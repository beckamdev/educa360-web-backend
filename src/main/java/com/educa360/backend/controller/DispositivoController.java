package com.educa360.backend.controller;

import com.educa360.backend.dto.DispositivoRequest;
import com.educa360.backend.dto.DispositivoResponse;
import com.educa360.backend.service.DispositivoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Registro de dispositivos para push nativo.
 *
 * Todos los endpoints son del usuario autenticado: el dueño del token sale
 * del JWT, nunca del body. La app móvil llama al POST en cada login y al
 * DELETE al cerrar sesión.
 */
@RestController
@RequestMapping("/api/v1/notificaciones/dispositivos")
public class DispositivoController {

    private final DispositivoService dispositivoService;

    public DispositivoController(DispositivoService dispositivoService) {
        this.dispositivoService = dispositivoService;
    }

    /** Upsert idempotente: el mismo token dos veces no crea dos filas. */
    @PostMapping
    public ResponseEntity<DispositivoResponse> registrar(
            @Valid @RequestBody DispositivoRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                dispositivoService.registrar(authentication.getName(), request));
    }

    /** Útil para verificar en producción que el token quedó registrado. */
    @GetMapping
    public ResponseEntity<List<DispositivoResponse>> listar(Authentication authentication) {
        return ResponseEntity.ok(
                dispositivoService.listar(authentication.getName()));
    }

    /** Baja idempotente al cerrar sesión. */
    @DeleteMapping
    public ResponseEntity<Void> eliminar(
            @RequestParam String token,
            Authentication authentication) {
        dispositivoService.eliminar(authentication.getName(), token);
        return ResponseEntity.noContent().build();
    }
}
