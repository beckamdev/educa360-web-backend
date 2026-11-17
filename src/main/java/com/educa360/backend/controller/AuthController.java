package com.educa360.backend.controller;

import com.educa360.backend.dto.AuthResponse;
import com.educa360.backend.dto.LoginRequest;
import com.educa360.backend.dto.MeResponse;
import com.educa360.backend.dto.TelefonosUpdateRequest;
import com.educa360.backend.dto.UsuarioBasicoRequest;
import com.educa360.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/login -> JWT + perfil
     *
     * La IP acompaña al email en el límite de intentos fallidos.
     * getRemoteAddr() es la IP real salvo que haya un proxy delante: en ese
     * caso activar server.forward-headers-strategy=framework (leer el
     * X-Forwarded-For a mano sería manipulable por el cliente).
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest.getRemoteAddr()));
    }

    /**
     * POST /api/v1/auth/register -> sólo funciona con la BD vacía: crea la
     * primera cuenta (siempre ADMIN). En cuanto existe una, responde 403 y
     * las altas pasan a /api/v1/admin/*.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody UsuarioBasicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** GET /api/v1/auth/me -> la cuenta + el perfil de su rol (título/materias del docente) */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.me(authentication.getName()));
    }

    /**
     * PATCH /api/v1/auth/me/telefonos -> el usuario reemplaza sus propios
     * teléfonos. La lista enviada es la que queda; {@code []} los quita todos.
     * Devuelve el perfil completo, igual que {@code GET /auth/me}.
     */
    @PatchMapping("/me/telefonos")
    public ResponseEntity<MeResponse> actualizarTelefonos(
            @Valid @RequestBody TelefonosUpdateRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                authService.actualizarTelefonos(authentication.getName(), datos.telefonos()));
    }
}
