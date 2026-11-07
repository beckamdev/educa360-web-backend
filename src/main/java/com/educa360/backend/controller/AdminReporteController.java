package com.educa360.backend.controller;

import com.educa360.backend.dto.ReporteAsistenciaResponse;
import com.educa360.backend.dto.ReporteCalificacionesResponse;
import com.educa360.backend.service.CierreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes anuales de asistencia y calificaciones para ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/reportes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReporteController {

    private final CierreService cierreService;

    public AdminReporteController(CierreService cierreService) {
        this.cierreService = cierreService;
    }

    @GetMapping("/asistencia")
    public ResponseEntity<ReporteAsistenciaResponse> asistencia(
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId,
            @RequestParam(required = false) Long seccionId) {
        return ResponseEntity.ok(
                cierreService.reporteAsistencia(anioId, bimestreId, seccionId));
    }

    @GetMapping("/calificaciones")
    public ResponseEntity<ReporteCalificacionesResponse> calificaciones(
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long bimestreId,
            @RequestParam(required = false) Long seccionId) {
        return ResponseEntity.ok(
                cierreService.reporteCalificaciones(anioId, bimestreId, seccionId));
    }
}
