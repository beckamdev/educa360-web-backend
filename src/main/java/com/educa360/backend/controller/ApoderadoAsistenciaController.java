package com.educa360.backend.controller;

import com.educa360.backend.dto.AsistenciaResumenResponse;
import com.educa360.backend.service.AsistenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistencia de un HIJO del apoderado autenticado.
 *
 * El vínculo lo crea Secretaría; si el hijo no está vinculado o no está
 * activo, responde 404. El resumen incluye el porcentaje del período.
 */
@RestController
@RequestMapping("/api/v1/apoderado/hijos")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoAsistenciaController {

    private final AsistenciaService asistenciaService;

    public ApoderadoAsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @GetMapping("/{hijoId}/asistencias")
    public ResponseEntity<AsistenciaResumenResponse> asistenciasDelHijo(
            @PathVariable Long hijoId,
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId,
            Authentication authentication) {
        return ResponseEntity.ok(asistenciaService.resumenDelHijo(
                authentication.getName(), hijoId, anioId, bimestreId));
    }
}
