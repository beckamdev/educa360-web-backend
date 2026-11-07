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

/** Materiales publicados de un HIJO vinculado, en un curso de su sección. */
@RestController
@RequestMapping("/api/v1/apoderado/hijos")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoMaterialController {

    private final MaterialService materialService;

    public ApoderadoMaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/{hijoId}/cursos/{cursoId}/materiales")
    public ResponseEntity<List<MaterialResponse>> listar(
            @PathVariable Long hijoId,
            @PathVariable Long cursoId,
            Authentication authentication) {
        return ResponseEntity.ok(materialService.listarDelApoderado(
                authentication.getName(), hijoId, cursoId));
    }
}
