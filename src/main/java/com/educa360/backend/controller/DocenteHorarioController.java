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
 * Zona del DOCENTE: su semana completa en una sola petición.
 *
 * Todos los bloques de SUS cursos, con materia y sección, ordenados por día
 * (LUNES→SÁBADO) y hora. Con esto el cliente pinta "clases de hoy" sin una
 * llamada por curso. La identidad sale del token.
 */
@RestController
@RequestMapping("/api/v1/docente/horario")
@PreAuthorize("hasRole('DOCENTE')")
public class DocenteHorarioController {

    private final CursoService cursoService;

    public DocenteHorarioController(CursoService cursoService) {
        this.cursoService = cursoService;
    }

    @GetMapping
    public ResponseEntity<List<HorarioSeccionResponse>> miHorario(Authentication authentication) {
        return ResponseEntity.ok(cursoService.listarHorarioDelDocente(authentication.getName()));
    }
}
