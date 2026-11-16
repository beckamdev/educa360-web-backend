package com.educa360.backend.controller;

import com.educa360.backend.dto.HorarioSeccionResponse;
import com.educa360.backend.service.CursoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Zona del ESTUDIANTE: el horario de SU sección (la del período vigente).
 *
 * Mismo criterio que /estudiante/cursos: sin filtros, la identidad sale del
 * token. Sin matrícula (o sin período abierto) devuelve lista vacía — no es
 * un error, es un alumno aún no inscrito.
 */
@RestController
@RequestMapping("/api/v1/estudiante/horario")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class EstudianteHorarioController {

    private final CursoService cursoService;

    public EstudianteHorarioController(CursoService cursoService) {
        this.cursoService = cursoService;
    }

    @GetMapping
    public ResponseEntity<List<HorarioSeccionResponse>> miHorario(Authentication authentication) {
        return ResponseEntity.ok(cursoService.listarHorarioDelEstudiante(authentication.getName()));
    }
}
