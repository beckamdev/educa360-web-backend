package com.educa360.backend.controller;

import com.educa360.backend.dto.UnidadResponse;
import com.educa360.backend.service.PlanificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Planificación visible para el APODERADO: sólo unidades PUBLICADA de los
 * cursos de sus hijos activos y vinculados. El vínculo lo crea Secretaría.
 */
@RestController
@RequestMapping("/api/v1/apoderado")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoPlanificacionController {

    private final PlanificacionService planificacionService;

    public ApoderadoPlanificacionController(PlanificacionService planificacionService) {
        this.planificacionService = planificacionService;
    }

    @GetMapping("/hijos/{hijoId}/cursos/{cursoId}/unidades")
    public ResponseEntity<List<UnidadResponse>> listarUnidades(
            @PathVariable Long hijoId,
            @PathVariable Long cursoId,
            Authentication authentication) {
        return ResponseEntity.ok(planificacionService.listarDelApoderado(
                authentication.getName(), hijoId, cursoId));
    }
}
