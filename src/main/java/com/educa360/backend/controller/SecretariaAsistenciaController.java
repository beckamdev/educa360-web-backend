package com.educa360.backend.controller;

import com.educa360.backend.dto.AsistenciaResumenResponse;
import com.educa360.backend.service.AsistenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistencia de un alumno vista por SECRETARÍA (y ADMIN como superconjunto).
 *
 * Es lectura: Secretaría no registra asistencia, pero necesita ver el
 * historial para resolver justificaciones (M7) y atender a las familias.
 */
@RestController
@RequestMapping("/api/v1/secretaria/estudiantes")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
public class SecretariaAsistenciaController {

    private final AsistenciaService asistenciaService;

    public SecretariaAsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @GetMapping("/{id}/asistencias")
    public ResponseEntity<AsistenciaResumenResponse> asistenciasDelEstudiante(
            @PathVariable Long id,
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId) {
        return ResponseEntity.ok(
                asistenciaService.resumenDeEstudiante(id, anioId, bimestreId));
    }
}
