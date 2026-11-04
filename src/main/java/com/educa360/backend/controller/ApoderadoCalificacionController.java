package com.educa360.backend.controller;

import com.educa360.backend.dto.CalificacionResumenResponse;
import com.educa360.backend.service.CalificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calificaciones de un HIJO del apoderado autenticado.
 *
 * El vínculo lo crea Secretaría; si el hijo no está vinculado o no está
 * activo, responde 404. Sólo se ven notas de unidades PUBLICADA.
 */
@RestController
@RequestMapping("/api/v1/apoderado/hijos")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoCalificacionController {

    private final CalificacionService calificacionService;

    public ApoderadoCalificacionController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    @GetMapping("/{hijoId}/calificaciones")
    public ResponseEntity<CalificacionResumenResponse> calificacionesDelHijo(
            @PathVariable Long hijoId,
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId,
            Authentication authentication) {
        return ResponseEntity.ok(calificacionService.resumenDelHijo(
                authentication.getName(), hijoId, anioId, bimestreId));
    }
}
