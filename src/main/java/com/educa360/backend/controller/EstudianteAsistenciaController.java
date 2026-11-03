package com.educa360.backend.controller;

import com.educa360.backend.dto.AsistenciaResumenResponse;
import com.educa360.backend.service.AsistenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistencia del ESTUDIANTE autenticado.
 *
 * Sin filtros usa su matrícula del año vigente; con {@code anioId} consulta
 * el año indicado. El resumen incluye el porcentaje sobre asistencias
 * registradas (mínimo 70% para aprobar).
 */
@RestController
@RequestMapping("/api/v1/estudiante/asistencias")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class EstudianteAsistenciaController {

    private final AsistenciaService asistenciaService;

    public EstudianteAsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @GetMapping
    public ResponseEntity<AsistenciaResumenResponse> misAsistencias(
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId,
            Authentication authentication) {
        return ResponseEntity.ok(asistenciaService.resumenDelEstudiante(
                authentication.getName(), anioId, bimestreId));
    }
}
