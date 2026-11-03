package com.educa360.backend.controller;

import com.educa360.backend.dto.BimestreEstadoRequest;
import com.educa360.backend.dto.BimestreRequest;
import com.educa360.backend.dto.BimestreResponse;
import com.educa360.backend.service.BimestreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Bimestres (exclusivo de ADMIN).
 *
 * Alta y listado cuelgan del año (/admin/anios/{id}/bimestres); la edición y
 * el estado se direccionan por el id del bimestre.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class BimestreController {

    private final BimestreService bimestreService;

    public BimestreController(BimestreService bimestreService) {
        this.bimestreService = bimestreService;
    }

    @GetMapping("/anios/{anioId}/bimestres")
    public ResponseEntity<List<BimestreResponse>> listar(@PathVariable Long anioId) {
        return ResponseEntity.ok(bimestreService.listar(anioId));
    }

    @PostMapping("/anios/{anioId}/bimestres")
    public ResponseEntity<BimestreResponse> crear(
            @PathVariable Long anioId, @Valid @RequestBody BimestreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bimestreService.crear(anioId, request));
    }

    @PutMapping("/bimestres/{id}")
    public ResponseEntity<BimestreResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody BimestreRequest request) {
        return ResponseEntity.ok(bimestreService.actualizar(id, request));
    }

    /** {"estado": "PLANIFICADO"|"ABIERTO"|"CERRADO"}. */
    @PatchMapping("/bimestres/{id}/estado")
    public ResponseEntity<BimestreResponse> cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody BimestreEstadoRequest request) {
        return ResponseEntity.ok(bimestreService.cambiarEstado(id, request.estado()));
    }
}
