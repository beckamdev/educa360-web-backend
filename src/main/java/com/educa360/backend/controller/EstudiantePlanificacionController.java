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
 * Planificación visible para el ESTUDIANTE: sólo unidades PUBLICADA de los
 * cursos de su sección vigente. La identidad sale del token.
 */
@RestController
@RequestMapping("/api/v1/estudiante")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class EstudiantePlanificacionController {

    private final PlanificacionService planificacionService;

    public EstudiantePlanificacionController(PlanificacionService planificacionService) {
        this.planificacionService = planificacionService;
    }

    @GetMapping("/cursos/{cursoId}/unidades")
    public ResponseEntity<List<UnidadResponse>> listarUnidades(
            @PathVariable Long cursoId, Authentication authentication) {
        return ResponseEntity.ok(
                planificacionService.listarDelEstudiante(authentication.getName(), cursoId));
    }
}
