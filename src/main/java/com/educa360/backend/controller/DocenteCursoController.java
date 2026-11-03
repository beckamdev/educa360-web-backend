package com.educa360.backend.controller;

import com.educa360.backend.dto.CursoResponse;
import com.educa360.backend.dto.HorarioBloqueResponse;
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
 * Zona del DOCENTE: SUS cursos (la materia, la sección, el año) y su horario.
 *
 * Convenio de autorización del proyecto: la ruta nombra al dueño de los
 * datos y el @PreAuthorize dice quién puede entrar. El docente no pasa
 * filtros: su identidad sale del token, que es quien no puede falsearla —
 * sólo ve lo suyo (§5, acceso a datos por perfil).
 */
@RestController
@RequestMapping("/api/v1/docente/cursos")
@PreAuthorize("hasRole('DOCENTE')")
public class DocenteCursoController {

    private final CursoService cursoService;

    public DocenteCursoController(CursoService cursoService) {
        this.cursoService = cursoService;
    }

    @GetMapping
    public ResponseEntity<List<CursoResponse>> misCursos(Authentication authentication) {
        return ResponseEntity.ok(cursoService.listarDelDocente(authentication.getName()));
    }

    /** Horario de un curso propio: un docente no puede ver el de un curso ajeno. */
    @GetMapping("/{id}/horario")
    public ResponseEntity<List<HorarioBloqueResponse>> horario(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(cursoService.listarHorarioDelDocente(authentication.getName(), id));
    }
}
