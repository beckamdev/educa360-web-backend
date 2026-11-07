package com.educa360.backend.controller;

import com.educa360.backend.dto.MatriculaResponse;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.service.MatriculaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Zona del ESTUDIANTE: su matrícula y sección del año vigente.
 *
 * La identidad sale del token: no recibe ids, no puede consultar la
 * matrícula de otro alumno. Sin año abierto o sin matrícula devuelve 404.
 */
@RestController
@RequestMapping("/api/v1/estudiante/matricula")
@PreAuthorize("hasRole('ESTUDIANTE')")
public class EstudianteMatriculaController {

    private final MatriculaService matriculaService;

    public EstudianteMatriculaController(MatriculaService matriculaService) {
        this.matriculaService = matriculaService;
    }

    @GetMapping
    public ResponseEntity<MatriculaResponse> miMatricula(Authentication authentication) {
        MatriculaResponse matricula = matriculaService.vigenteDelUsuario(authentication.getName())
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante no tiene matrícula en el año vigente"));
        return ResponseEntity.ok(matricula);
    }
}
