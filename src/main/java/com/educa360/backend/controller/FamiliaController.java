package com.educa360.backend.controller;

import com.educa360.backend.dto.FamiliaCreateRequest;
import com.educa360.backend.dto.FamiliaResponse;
import com.educa360.backend.service.FamiliaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de familias para SECRETARÍA (ADMIN como superconjunto).
 *
 * Resuelve en una sola petición el flujo "buscar apoderado por DNI; si no
 * existe, crearlo; crear el estudiante y vincularlos".
 */
@RestController
@RequestMapping("/api/v1/secretaria/familias")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
public class FamiliaController {

    private final FamiliaService familiaService;

    public FamiliaController(FamiliaService familiaService) {
        this.familiaService = familiaService;
    }

    @PostMapping
    public ResponseEntity<FamiliaResponse> crear(
            @Valid @RequestBody FamiliaCreateRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familiaService.crear(datos));
    }
}
