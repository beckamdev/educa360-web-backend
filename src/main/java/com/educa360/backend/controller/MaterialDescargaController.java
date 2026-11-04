package com.educa360.backend.controller;

import com.educa360.backend.dto.ArchivoDescargaResponse;
import com.educa360.backend.service.MaterialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Descarga común de materiales. El service decide según el rol y el
 * ownership; un recurso no visible responde 404.
 */
@RestController
@RequestMapping("/api/v1/materiales")
@PreAuthorize("isAuthenticated()")
public class MaterialDescargaController {

    private final MaterialService materialService;

    public MaterialDescargaController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ArchivoDescargaResponse> descargar(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(materialService.descargar(authentication.getName(), id));
    }
}
