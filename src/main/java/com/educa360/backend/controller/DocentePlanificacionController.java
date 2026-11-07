package com.educa360.backend.controller;

import com.educa360.backend.dto.ContenidoRequest;
import com.educa360.backend.dto.ContenidoResponse;
import com.educa360.backend.dto.ReordenarContenidosRequest;
import com.educa360.backend.dto.UnidadCreateRequest;
import com.educa360.backend.dto.UnidadEstadoRequest;
import com.educa360.backend.dto.UnidadResponse;
import com.educa360.backend.dto.UnidadUpdateRequest;
import com.educa360.backend.service.PlanificacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Planificación del DOCENTE: unidades de sus cursos y contenidos de cada unidad.
 *
 * La identidad sale del token: el docente no pasa su id ni puede consultar
 * cursos ajenos. Un curso o unidad ajena responde 404, no 403, para no
 * revelar que existe.
 */
@RestController
@RequestMapping("/api/v1/docente")
@PreAuthorize("hasRole('DOCENTE')")
public class DocentePlanificacionController {

    private final PlanificacionService planificacionService;

    public DocentePlanificacionController(PlanificacionService planificacionService) {
        this.planificacionService = planificacionService;
    }

    // ==================== Unidades ====================

    @GetMapping("/cursos/{cursoId}/unidades")
    public ResponseEntity<List<UnidadResponse>> listarUnidades(
            @PathVariable Long cursoId, Authentication authentication) {
        return ResponseEntity.ok(
                planificacionService.listarDelCurso(authentication.getName(), cursoId));
    }

    @PostMapping("/cursos/{cursoId}/unidades")
    public ResponseEntity<UnidadResponse> crearUnidad(
            @PathVariable Long cursoId,
            @Valid @RequestBody UnidadCreateRequest datos,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                planificacionService.crear(authentication.getName(), cursoId, datos));
    }

    @GetMapping("/unidades/{id}")
    public ResponseEntity<UnidadResponse> buscarUnidad(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(planificacionService.buscar(authentication.getName(), id));
    }

    @PutMapping("/unidades/{id}")
    public ResponseEntity<UnidadResponse> actualizarUnidad(
            @PathVariable Long id,
            @Valid @RequestBody UnidadUpdateRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                planificacionService.actualizar(authentication.getName(), id, datos));
    }

    /** {"estado":"BORRADOR"|"PUBLICADA"}. */
    @PatchMapping("/unidades/{id}/estado")
    public ResponseEntity<UnidadResponse> cambiarEstadoUnidad(
            @PathVariable Long id,
            @Valid @RequestBody UnidadEstadoRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                planificacionService.cambiarEstado(authentication.getName(), id, datos.estado()));
    }

    @DeleteMapping("/unidades/{id}")
    public ResponseEntity<Void> eliminarUnidad(
            @PathVariable Long id, Authentication authentication) {
        planificacionService.eliminar(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Contenidos ====================

    @GetMapping("/unidades/{id}/contenidos")
    public ResponseEntity<List<ContenidoResponse>> listarContenidos(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(
                planificacionService.listarContenidos(authentication.getName(), id));
    }

    @PostMapping("/unidades/{id}/contenidos")
    public ResponseEntity<ContenidoResponse> crearContenido(
            @PathVariable Long id,
            @Valid @RequestBody ContenidoRequest datos,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                planificacionService.crearContenido(authentication.getName(), id, datos));
    }

    @PutMapping("/contenidos/{id}")
    public ResponseEntity<ContenidoResponse> actualizarContenido(
            @PathVariable Long id,
            @Valid @RequestBody ContenidoRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                planificacionService.actualizarContenido(authentication.getName(), id, datos));
    }

    @DeleteMapping("/contenidos/{id}")
    public ResponseEntity<Void> eliminarContenido(
            @PathVariable Long id, Authentication authentication) {
        planificacionService.eliminarContenido(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /** {"contenidoIds":[3,1,2]} — debe traer todos los contenidos de la unidad. */
    @PutMapping("/unidades/{id}/contenidos/orden")
    public ResponseEntity<List<ContenidoResponse>> reordenarContenidos(
            @PathVariable Long id,
            @Valid @RequestBody ReordenarContenidosRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                planificacionService.reordenar(authentication.getName(), id, datos));
    }
}
