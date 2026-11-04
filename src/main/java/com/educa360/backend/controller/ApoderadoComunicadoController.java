package com.educa360.backend.controller;

import com.educa360.backend.dto.ComunicadoResponse;
import com.educa360.backend.service.ComunicadoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Comunicados visibles para el APODERADO: generales y los de la sección de
 * cada hijo activo con matrícula vigente.
 */
@RestController
@RequestMapping("/api/v1/apoderado/comunicados")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoComunicadoController {

    private final ComunicadoService comunicadoService;

    public ApoderadoComunicadoController(ComunicadoService comunicadoService) {
        this.comunicadoService = comunicadoService;
    }

    @GetMapping
    public ResponseEntity<List<ComunicadoResponse>> listar(Authentication authentication) {
        return ResponseEntity.ok(
                comunicadoService.listarApoderado(authentication.getName()));
    }
}
