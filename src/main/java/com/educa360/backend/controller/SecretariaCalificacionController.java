package com.educa360.backend.controller;

import com.educa360.backend.dto.CalificacionResumenResponse;
import com.educa360.backend.service.CalificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calificaciones de un alumno vista por SECRETARÍA.
 *
 * Es lectura: Secretaría no registra ni corrige notas. A diferencia de la
 * vista del alumno, incluye también unidades en BORRADOR.
 */
@RestController
@RequestMapping("/api/v1/secretaria/estudiantes")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
public class SecretariaCalificacionController {

    private final CalificacionService calificacionService;

    public SecretariaCalificacionController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    @GetMapping("/{id}/calificaciones")
    public ResponseEntity<CalificacionResumenResponse> calificacionesDelEstudiante(
            @PathVariable Long id,
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId) {
        return ResponseEntity.ok(
                calificacionService.resumenDeEstudiante(id, anioId, bimestreId));
    }
}
