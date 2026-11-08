package com.educa360.backend.controller;

import com.educa360.backend.dto.ApoderadoBusquedaResponse;
import com.educa360.backend.dto.ApoderadoRequest;
import com.educa360.backend.dto.ApoderadoResponse;
import com.educa360.backend.dto.HijoResponse;
import com.educa360.backend.dto.RepresentadoRequest;
import com.educa360.backend.dto.UsuarioBasicoRequest;
import com.educa360.backend.dto.UsuarioResponse;
import com.educa360.backend.service.ApoderadoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Altas y gestión de APODERADOS (cuenta + representados).
 *
 * Mismo criterio que EstudianteController: dominio de SECRETARIA con ADMIN
 * como superconjunto (la ruta = dominio responsable; el @PreAuthorize =
 * quién tiene permiso). Los vínculos con sus hijos los CREA aquí Secretaría;
 * el apoderado sólo los consulta desde /apoderado/hijos.
 *
 * Cuerpo de alta: {"usuario": {...}, "perfil": {...}}.
 */
@RestController
@RequestMapping("/api/v1/secretaria/apoderados")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
public class ApoderadoController {

    private final ApoderadoService apoderadoService;

    public ApoderadoController(ApoderadoService apoderadoService) {
        this.apoderadoService = apoderadoService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(
            @Valid @RequestBody Cuerpo body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apoderadoService.crear(body.usuario(), body.perfil()));
    }

    /** Listado de familias con sus hijos básicos (buscable con ?q=). */
    @GetMapping
    public ResponseEntity<List<ApoderadoResponse>> listar(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(apoderadoService.listar(q));
    }

    /**
     * Búsqueda exacta por DNI para el alta de familias.
     *
     * Responde 200 con {@code existe=false} si no hay apoderado: el frontend
     * usa esa rama para mostrar el formulario de alta del apoderado.
     */
    @GetMapping("/por-dni/{dni}")
    public ResponseEntity<ApoderadoBusquedaResponse> buscarPorDni(
            @PathVariable String dni) {
        return ResponseEntity.ok(apoderadoService.buscarPorDni(dni));
    }

    /**
     * Vincula un estudiante al apoderado (o reactiva el par dado de baja):
     * {"estudianteId":1,"parentesco":"MADRE","responsablePrincipal":true}.
     * Sólo un responsable principal por estudiante: al nombrar uno nuevo,
     * el anterior deja de serlo.
     */
    @PostMapping("/{id}/representados")
    public ResponseEntity<HijoResponse> vincular(
            @PathVariable Long id,
            @Valid @RequestBody RepresentadoRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apoderadoService.vincular(id, datos));
    }

    /** Baja lógica del vínculo: el apoderado deja de ver al hijo (queda el historial). */
    @DeleteMapping("/{id}/representados/{estudianteId}")
    public ResponseEntity<Void> desvincular(
            @PathVariable Long id,
            @PathVariable Long estudianteId) {
        apoderadoService.desvincular(id, estudianteId);
        return ResponseEntity.noContent().build();
    }

    /** Envoltorio del request: parte común (usuario) + parte del rol (perfil). */
    public record Cuerpo(
            @Valid UsuarioBasicoRequest usuario,
            @Valid @NotNull ApoderadoRequest perfil) {
    }
}
