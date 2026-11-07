package com.educa360.backend.controller;

import com.educa360.backend.dto.MaterialResponse;
import com.educa360.backend.service.MaterialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lectura de materiales para ADMIN: incluye no publicados, no modifica. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaterialController {

    private final MaterialService materialService;

    public AdminMaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/cursos/{cursoId}/materiales")
    public ResponseEntity<List<MaterialResponse>> listar(@PathVariable Long cursoId) {
        return ResponseEntity.ok(materialService.listarDelCursoAdmin(cursoId));
    }
}
