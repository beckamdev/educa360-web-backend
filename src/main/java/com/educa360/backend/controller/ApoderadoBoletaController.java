package com.educa360.backend.controller;

import com.educa360.backend.dto.BoletaResponse;
import com.educa360.backend.service.CierreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Boleta anual de un hijo del APODERADO (sólo vínculos activos). */
@RestController
@RequestMapping("/api/v1/apoderado/hijos")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoBoletaController {

    private final CierreService cierreService;

    public ApoderadoBoletaController(CierreService cierreService) {
        this.cierreService = cierreService;
    }

    @GetMapping("/{id}/boleta")
    public ResponseEntity<BoletaResponse> boleta(
            @PathVariable Long id,
            @RequestParam(required = false) Long anioId,
            Authentication authentication) {
        return ResponseEntity.ok(
                cierreService.boletaDelHijo(authentication.getName(), id, anioId));
    }
}
