package com.educa360.backend.controller;

import com.educa360.backend.dto.ComunicadoCreateRequest;
import com.educa360.backend.dto.ComunicadoResponse;
import com.educa360.backend.dto.ComunicadoUpdateRequest;
import com.educa360.backend.service.ComunicadoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Comunicados: ADMIN publica generales o por sección y gestiona cualquiera. */
@RestController
@RequestMapping("/api/v1/admin/comunicados")
@PreAuthorize("hasRole('ADMIN')")
public class AdminComunicadoController {

    private final ComunicadoService comunicadoService;

    public AdminComunicadoController(ComunicadoService comunicadoService) {
        this.comunicadoService = comunicadoService;
    }

    @GetMapping
    public ResponseEntity<List<ComunicadoResponse>> listar(
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long seccionId) {
        return ResponseEntity.ok(comunicadoService.listarAdmin(anioId, seccionId));
    }

    @PostMapping
    public ResponseEntity<ComunicadoResponse> crear(
            @Valid @RequestBody ComunicadoCreateRequest datos,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                comunicadoService.crearAdmin(authentication.getName(), datos));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComunicadoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ComunicadoUpdateRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                comunicadoService.actualizarAdmin(authentication.getName(), id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
                                         Authentication authentication) {
        comunicadoService.eliminarAdmin(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
