package com.educa360.backend.controller;

import com.educa360.backend.dto.MaterialResponse;
import com.educa360.backend.service.MaterialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Materiales publicados de los cursos de la sección vigente del ESTUDIANTE. */
@RestController
@RequestMapping("/api/v1/estudiante")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class EstudianteMaterialController {

    private final MaterialService materialService;

    public EstudianteMaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/cursos/{cursoId}/materiales")
    public ResponseEntity<List<MaterialResponse>> listar(
            @PathVariable Long cursoId, Authentication authentication) {
        return ResponseEntity.ok(
                materialService.listarDelEstudiante(authentication.getName(), cursoId));
    }
}
