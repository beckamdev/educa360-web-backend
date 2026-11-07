package com.educa360.backend.controller;

import com.educa360.backend.dto.CambioSeccionRequest;
import com.educa360.backend.dto.MatriculaRequest;
import com.educa360.backend.dto.MatriculaResponse;
import com.educa360.backend.service.MatriculaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Matrículas — dominio de SECRETARIA con ADMIN como superconjunto (§3).
 *
 * La matrícula es el registro académico anual del alumno; no representa
 * pagos ni pensiones. Nace EN_CURSO y sólo cambia de situación por retiro
 * o por el cierre del año.
 */
@RestController
@RequestMapping("/api/v1/secretaria/matriculas")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
public class MatriculaController {

    private final MatriculaService matriculaService;

    public MatriculaController(MatriculaService matriculaService) {
        this.matriculaService = matriculaService;
    }

    /** Inscribe al alumno en la sección. */
    @PostMapping
    public ResponseEntity<MatriculaResponse> crear(
            @Valid @RequestBody MatriculaRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(matriculaService.crear(datos));
    }

    /** Listado filtrable por año, sección y texto (nombre/DNI/email). */
    @GetMapping
    public ResponseEntity<List<MatriculaResponse>> listar(
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) Long seccionId,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(matriculaService.listar(anioId, seccionId, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatriculaResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(matriculaService.buscar(id));
    }

    /** Cambia de sección antes de iniciar clases y sin actividad académica. */
    @PatchMapping("/{id}/seccion")
    public ResponseEntity<MatriculaResponse> cambiarSeccion(
            @PathVariable Long id,
            @Valid @RequestBody CambioSeccionRequest datos) {
        return ResponseEntity.ok(matriculaService.cambiarSeccion(id, datos));
    }

    /** Retira al alumno del año: matrícula y estudiante quedan RETIRADOS. */
    @PatchMapping("/{id}/retiro")
    public ResponseEntity<MatriculaResponse> retirar(@PathVariable Long id) {
        return ResponseEntity.ok(matriculaService.retirar(id));
    }
}
