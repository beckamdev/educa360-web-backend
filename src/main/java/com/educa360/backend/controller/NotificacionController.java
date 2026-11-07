package com.educa360.backend.controller;

import com.educa360.backend.dto.ContadorNotificacionesResponse;
import com.educa360.backend.dto.NotificacionResponse;
import com.educa360.backend.service.NotificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bandeja de notificaciones del usuario autenticado.
 *
 * No recibe el id del destinatario: sale del token. Cualquier usuario
 * autenticado puede consultar y marcar solo las suyas.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public ResponseEntity<List<NotificacionResponse>> listar(
            @RequestParam(required = false) Boolean leida,
            Authentication authentication) {
        return ResponseEntity.ok(
                notificacionService.listar(authentication.getName(), leida));
    }

    @GetMapping("/contador")
    public ResponseEntity<ContadorNotificacionesResponse> contador(
            Authentication authentication) {
        return ResponseEntity.ok(
                notificacionService.contador(authentication.getName()));
    }

    @PatchMapping("/{id}/leida")
    public ResponseEntity<NotificacionResponse> marcarLeida(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(
                notificacionService.marcarLeida(authentication.getName(), id));
    }

    @PatchMapping("/leidas")
    public ResponseEntity<ContadorNotificacionesResponse> marcarTodasLeidas(
            Authentication authentication) {
        return ResponseEntity.ok(
                notificacionService.marcarTodasLeidas(authentication.getName()));
    }
}
