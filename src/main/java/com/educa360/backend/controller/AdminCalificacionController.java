package com.educa360.backend.controller;

import com.educa360.backend.dto.ActaCursoResponse;
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
 * Lectura de calificaciones para ADMIN (matriz §4.2). No registra ni corrige:
 * eso es del docente dueño del curso.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCalificacionController {

    private final CalificacionService calificacionService;

    public AdminCalificacionController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    @GetMapping("/estudiantes/{id}/calificaciones")
    public ResponseEntity<CalificacionResumenResponse> calificacionesDelEstudiante(
            @PathVariable Long id,
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId) {
        return ResponseEntity.ok(
                calificacionService.resumenDeEstudiante(id, anioId, bimestreId));
    }

    @GetMapping("/cursos/{id}/actas")
    public ResponseEntity<ActaCursoResponse> acta(
            @PathVariable Long id, @RequestParam Long bimestreId) {
        return ResponseEntity.ok(calificacionService.actaAdmin(id, bimestreId));
    }
}
