package com.educa360.backend.controller;

import com.educa360.backend.dto.HorarioSeccionResponse;
import com.educa360.backend.dto.SeccionRequest;
import com.educa360.backend.dto.SeccionResponse;
import com.educa360.backend.service.CursoService;
import com.educa360.backend.service.SeccionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Secciones — los grupos de alumnos ("3°A") — exclusivo de ADMIN.
 *
 * Sólo secundaria: grado 1..5 y sin campo "nivel" (lo valida el DTO).
 * Un "grado+letra" sólo puede existir una vez dentro de un año → 409.
 */
@RestController
@RequestMapping("/api/v1/admin/secciones")
@PreAuthorize("hasRole('ADMIN')")
public class SeccionController {

    private final SeccionService seccionService;
    private final CursoService cursoService;

    public SeccionController(SeccionService seccionService, CursoService cursoService) {
        this.seccionService = seccionService;
        this.cursoService = cursoService;
    }

    /** Listado completo, o sólo las de un año con ?anioId=. */
    @GetMapping
    public ResponseEntity<List<SeccionResponse>> listar(@RequestParam(required = false) Long anioId) {
        return ResponseEntity.ok(seccionService.listar(anioId));
    }

    @PostMapping
    public ResponseEntity<SeccionResponse> crear(@Valid @RequestBody SeccionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seccionService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SeccionResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody SeccionRequest request) {
        return ResponseEntity.ok(seccionService.actualizar(id, request));
    }

    /**
     * Horario completo de la sección: todos los bloques de sus cursos, con
     * materia y docente, ordenados por día y hora. Una sola petición.
     */
    @GetMapping("/{id}/horario")
    public ResponseEntity<List<HorarioSeccionResponse>> listarHorario(@PathVariable Long id) {
        return ResponseEntity.ok(cursoService.listarHorarioDeSeccion(id));
    }
}
