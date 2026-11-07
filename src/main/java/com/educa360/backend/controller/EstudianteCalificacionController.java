package com.educa360.backend.controller;

import com.educa360.backend.dto.CalificacionResumenResponse;
import com.educa360.backend.service.CalificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calificaciones del ESTUDIANTE autenticado.
 *
 * Sin filtros usa su matrícula del año vigente; con {@code anioId} consulta
 * el año indicado. Sólo se ven notas de unidades PUBLICADA. El resumen incluye
 * promedios por unidad, por curso y general.
 */
@RestController
@RequestMapping("/api/v1/estudiante/calificaciones")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class EstudianteCalificacionController {

    private final CalificacionService calificacionService;

    public EstudianteCalificacionController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    @GetMapping
    public ResponseEntity<CalificacionResumenResponse> misCalificaciones(
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId,
            Authentication authentication) {
        return ResponseEntity.ok(calificacionService.resumenDelEstudiante(
                authentication.getName(), anioId, bimestreId));
    }
}
