package com.educa360.backend.controller;

import com.educa360.backend.dto.AnioEscolarRequest;
import com.educa360.backend.dto.AnioEscolarResponse;
import com.educa360.backend.dto.AnioEstadoRequest;
import com.educa360.backend.service.AnioEscolarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Años escolares (exclusivo de ADMIN).
 *
 * PATCH /{id}/estado es la única puerta para abrir un año — y sólo puede
 * haber UNO ABIERTO a la vez (si no, 409). Un año CERRADO es solo lectura.
 */
@RestController
@RequestMapping("/api/v1/admin/anios")
@PreAuthorize("hasRole('ADMIN')")
public class AnioEscolarController {

    private final AnioEscolarService anioService;

    public AnioEscolarController(AnioEscolarService anioService) {
        this.anioService = anioService;
    }

    @GetMapping
    public ResponseEntity<List<AnioEscolarResponse>> listar() {
        return ResponseEntity.ok(anioService.listar());
    }

    @PostMapping
    public ResponseEntity<AnioEscolarResponse> crear(@Valid @RequestBody AnioEscolarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anioService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnioEscolarResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody AnioEscolarRequest request) {
        return ResponseEntity.ok(anioService.actualizar(id, request));
    }

    /** {"estado": "PLANIFICADO"|"ABIERTO"|"CERRADO"}. */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<AnioEscolarResponse> cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody AnioEstadoRequest request) {
        return ResponseEntity.ok(anioService.cambiarEstado(id, request.estado()));
    }
}
