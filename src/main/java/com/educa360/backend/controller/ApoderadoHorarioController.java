package com.educa360.backend.controller;

import com.educa360.backend.dto.HorarioSeccionResponse;
import com.educa360.backend.service.CursoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Horario de un HIJO del apoderado autenticado: la sección vigente del alumno.
 *
 * El vínculo lo crea Secretaría; si el hijo no está vinculado o no está
 * activo, responde 404. Sin matrícula en el año abierto devuelve lista vacía.
 */
@RestController
@RequestMapping("/api/v1/apoderado/hijos")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoHorarioController {

    private final CursoService cursoService;

    public ApoderadoHorarioController(CursoService cursoService) {
        this.cursoService = cursoService;
    }

    @GetMapping("/{hijoId}/horario")
    public ResponseEntity<List<HorarioSeccionResponse>> horarioDelHijo(
            @PathVariable Long hijoId, Authentication authentication) {
        return ResponseEntity.ok(
                cursoService.listarHorarioDelHijo(authentication.getName(), hijoId));
    }
}
