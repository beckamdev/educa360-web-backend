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
 * Comunicados del DOCENTE. Lee generales y los de sus secciones (donde dicta
 * clase o es tutor); publica y gestiona solo los de sus secciones.
 */
@RestController
@RequestMapping("/api/v1/docente/comunicados")
@PreAuthorize("hasRole('DOCENTE')")
public class DocenteComunicadoController {

    private final ComunicadoService comunicadoService;

    public DocenteComunicadoController(ComunicadoService comunicadoService) {
        this.comunicadoService = comunicadoService;
    }

    @GetMapping
    public ResponseEntity<List<ComunicadoResponse>> listar(
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long seccionId,
            Authentication authentication) {
        return ResponseEntity.ok(comunicadoService.listarDocente(
                authentication.getName(), anioId, seccionId));
    }

    @PostMapping
    public ResponseEntity<ComunicadoResponse> crear(
            @Valid @RequestBody ComunicadoCreateRequest datos,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                comunicadoService.crearDocente(authentication.getName(), datos));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComunicadoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ComunicadoUpdateRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                comunicadoService.actualizarDocente(authentication.getName(), id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
                                         Authentication authentication) {
        comunicadoService.eliminarDocente(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
