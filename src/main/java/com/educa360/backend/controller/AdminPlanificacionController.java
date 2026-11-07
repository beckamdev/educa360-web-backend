package com.educa360.backend.controller;

import com.educa360.backend.dto.UnidadResponse;
import com.educa360.backend.service.PlanificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lectura de planificación para ADMIN (matriz §4.2). No crea ni edita:
 * eso es del docente dueño del curso.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlanificacionController {

    private final PlanificacionService planificacionService;

    public AdminPlanificacionController(PlanificacionService planificacionService) {
        this.planificacionService = planificacionService;
    }

    @GetMapping("/cursos/{cursoId}/unidades")
    public ResponseEntity<List<UnidadResponse>> listarUnidades(@PathVariable Long cursoId) {
        return ResponseEntity.ok(planificacionService.listarDelCursoAdmin(cursoId));
    }

    @GetMapping("/unidades/{id}")
    public ResponseEntity<UnidadResponse> buscarUnidad(@PathVariable Long id) {
        return ResponseEntity.ok(planificacionService.buscarAdmin(id));
    }
}
