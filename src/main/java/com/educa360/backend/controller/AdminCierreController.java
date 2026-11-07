package com.educa360.backend.controller;

import com.educa360.backend.dto.CierreResponse;
import com.educa360.backend.service.CierreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cierre anual: preview y ejecución. Ambas rutas devuelven la misma estructura
 * para que el ADMIN vea exactamente lo que se aplicará.
 */
@RestController
@RequestMapping("/api/v1/admin/anios")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCierreController {

    private final CierreService cierreService;

    public AdminCierreController(CierreService cierreService) {
        this.cierreService = cierreService;
    }

    @GetMapping("/{id}/cierre/preview")
    public ResponseEntity<CierreResponse> preview(@PathVariable Long id) {
        return ResponseEntity.ok(cierreService.preview(id));
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<CierreResponse> cerrar(@PathVariable Long id) {
        return ResponseEntity.ok(cierreService.cerrar(id));
    }
}
