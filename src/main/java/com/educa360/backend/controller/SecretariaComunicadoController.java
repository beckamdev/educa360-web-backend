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

/**
 * Comunicados de SECRETARÍA: solo generales. El service rechaza cualquier
 * intento de dirigirlos a una sección.
 */
@RestController
@RequestMapping("/api/v1/secretaria/comunicados")
@PreAuthorize("hasRole('SECRETARIA')")
public class SecretariaComunicadoController {

    private final ComunicadoService comunicadoService;

    public SecretariaComunicadoController(ComunicadoService comunicadoService) {
        this.comunicadoService = comunicadoService;
    }

    @GetMapping
    public ResponseEntity<List<ComunicadoResponse>> listar(
            @RequestParam(required = false) Long anioId,
            Authentication authentication) {
        return ResponseEntity.ok(
                comunicadoService.listarSecretaria(authentication.getName(), anioId));
    }

    @PostMapping
    public ResponseEntity<ComunicadoResponse> crear(
            @Valid @RequestBody ComunicadoCreateRequest datos,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                comunicadoService.crearSecretaria(authentication.getName(), datos));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComunicadoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ComunicadoUpdateRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                comunicadoService.actualizarSecretaria(authentication.getName(), id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
                                         Authentication authentication) {
        comunicadoService.eliminarSecretaria(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
