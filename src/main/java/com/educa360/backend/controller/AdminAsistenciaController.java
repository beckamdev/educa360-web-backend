package com.educa360.backend.controller;

import com.educa360.backend.dto.AsistenciaClaseResponse;
import com.educa360.backend.dto.AsistenciaResumenResponse;
import com.educa360.backend.service.AsistenciaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Lectura de asistencia para ADMIN (matriz §4.2). No registra ni corrige:
 * eso es del docente dueño del curso.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAsistenciaController {

    private final AsistenciaService asistenciaService;

    public AdminAsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @GetMapping("/estudiantes/{id}/asistencias")
    public ResponseEntity<AsistenciaResumenResponse> asistenciasDelEstudiante(
            @PathVariable Long id,
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId) {
        return ResponseEntity.ok(
                asistenciaService.resumenDeEstudiante(id, anioId, bimestreId));
    }

    @GetMapping("/cursos/{cursoId}/asistencia")
    public ResponseEntity<AsistenciaClaseResponse> hojaDeClase(
            @PathVariable Long cursoId,
            @RequestParam Long bloqueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(
                asistenciaService.listarClaseAdmin(cursoId, bloqueId, fecha));
    }
}
