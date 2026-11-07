package com.educa360.backend.controller;

import com.educa360.backend.dto.BoletaResponse;
import com.educa360.backend.service.CierreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Boleta anual del propio ESTUDIANTE. */
@RestController
@RequestMapping("/api/v1/estudiante")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class EstudianteBoletaController {

    private final CierreService cierreService;

    public EstudianteBoletaController(CierreService cierreService) {
        this.cierreService = cierreService;
    }

    @GetMapping("/boleta")
    public ResponseEntity<BoletaResponse> boleta(
            @RequestParam(required = false) Long anioId,
            Authentication authentication) {
        return ResponseEntity.ok(
                cierreService.boletaDelEstudiante(authentication.getName(), anioId));
    }
}
