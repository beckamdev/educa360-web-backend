package com.educa360.backend.controller;

import com.educa360.backend.dto.HistorialEstudianteResponse;
import com.educa360.backend.service.CierreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Historial académico del alumno para SECRETARÍA (ADMIN como superconjunto). */
@RestController
@RequestMapping("/api/v1/secretaria/estudiantes")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
public class SecretariaHistorialController {

    private final CierreService cierreService;

    public SecretariaHistorialController(CierreService cierreService) {
        this.cierreService = cierreService;
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<HistorialEstudianteResponse> historial(@PathVariable Long id) {
        return ResponseEntity.ok(cierreService.historial(id));
    }
}
