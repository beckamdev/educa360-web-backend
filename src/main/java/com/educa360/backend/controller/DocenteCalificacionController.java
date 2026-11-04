package com.educa360.backend.controller;

import com.educa360.backend.dto.ActaCursoResponse;
import com.educa360.backend.dto.CalificacionBulkRequest;
import com.educa360.backend.dto.CalificacionUnidadResponse;
import com.educa360.backend.service.CalificacionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calificaciones del DOCENTE: hoja de una unidad, corrección, baja y acta.
 *
 * La identidad sale del token: el docente no puede tocar unidades ni cursos
 * ajenos. Sólo se califica en un bimestre ABIERTO.
 */
@RestController
@RequestMapping("/api/v1/docente")
@PreAuthorize("hasRole('DOCENTE')")
public class DocenteCalificacionController {

    private final CalificacionService calificacionService;

    public DocenteCalificacionController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    /** Hoja de la unidad: todos los alumnos activos y sus notas registradas. */
    @GetMapping("/unidades/{id}/calificaciones")
    public ResponseEntity<CalificacionUnidadResponse> listarUnidad(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(
                calificacionService.listarUnidad(authentication.getName(), id));
    }

    /** Upsert parcial de notas por (matrícula, criterio). */
    @PutMapping("/unidades/{id}/calificaciones")
    public ResponseEntity<CalificacionUnidadResponse> registrar(
            @PathVariable Long id,
            @Valid @RequestBody CalificacionBulkRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                calificacionService.registrar(authentication.getName(), id, datos));
    }

    /** Baja física de una nota, sólo mientras el bimestre está ABIERTO. */
    @DeleteMapping("/calificaciones/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
                                         Authentication authentication) {
        calificacionService.eliminar(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /** Acta del curso en un bimestre: alumno × unidades + promedio. */
    @GetMapping("/cursos/{id}/actas")
    public ResponseEntity<ActaCursoResponse> acta(
            @PathVariable Long id,
            @RequestParam Long bimestreId,
            Authentication authentication) {
        return ResponseEntity.ok(
                calificacionService.actaDelDocente(authentication.getName(), id, bimestreId));
    }
}
