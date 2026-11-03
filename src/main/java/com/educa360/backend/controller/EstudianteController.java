package com.educa360.backend.controller;

import com.educa360.backend.dto.EstudianteEstadoRequest;
import com.educa360.backend.dto.EstudianteRequest;
import com.educa360.backend.dto.EstudianteResponse;
import com.educa360.backend.dto.EstudianteUpdateRequest;
import com.educa360.backend.dto.UsuarioBasicoRequest;
import com.educa360.backend.dto.UsuarioResponse;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.service.EstudianteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Altas y ficha de ESTUDIANTE.
 *
 * Dominio de SECRETARIA (es quien recibe a las familias), con ADMIN como
 * superconjunto: la ruta nombra el dominio responsable y el @PreAuthorize
 * dice quién tiene permiso.
 *
 * Cuerpo del alta: {"usuario": {...}, "perfil": {...}}. EstudianteService
 * rechaza teléfonos en la cuenta: al ser menores, la comunicación es con
 * su apoderado.
 */
@RestController
@RequestMapping("/api/v1/secretaria/estudiantes")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
public class EstudianteController {

    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(
            @Valid @RequestBody Cuerpo body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(estudianteService.crear(body.usuario(), body.perfil()));
    }

    /**
     * Listado para Secretaría con EstudianteResponse (sección, matrícula y
     * apoderados) — NO UsuarioResponse: esa es la vista de cuentas de ADMIN.
     * Filtros: ?q= email/nombre/apellido/dni, ?estado= estado académico.
     */
    @GetMapping
    public ResponseEntity<List<EstudianteResponse>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Estudiante.EstadoEstudiante estado) {
        return ResponseEntity.ok(estudianteService.listar(q, estado));
    }

    /** Ficha completa del alumno. */
    @GetMapping("/{id}")
    public ResponseEntity<EstudianteResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(estudianteService.buscar(id));
    }

    /** Edición acotada: fecha de nacimiento y estado académico. */
    @PutMapping("/{id}")
    public ResponseEntity<EstudianteResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EstudianteUpdateRequest datos) {
        return ResponseEntity.ok(estudianteService.actualizar(id, datos));
    }

    /** Cambio de estado académico; ACTIVO es el reingreso. */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<EstudianteResponse> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody EstudianteEstadoRequest datos) {
        return ResponseEntity.ok(estudianteService.cambiarEstado(id, datos));
    }

    /** Envoltorio del request: parte común (usuario) + parte del rol (perfil). */
    public record Cuerpo(
            @Valid UsuarioBasicoRequest usuario,
            @Valid @NotNull EstudianteRequest perfil) {
    }
}
