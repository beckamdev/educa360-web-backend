package com.educa360.backend.controller;

import com.educa360.backend.dto.AsistenciaBulkRequest;
import com.educa360.backend.dto.AsistenciaClaseResponse;
import com.educa360.backend.service.AsistenciaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Asistencia del DOCENTE: hoja de una clase (bloque + fecha) y registro bulk.
 *
 * La identidad sale del token: el docente no puede consultar ni registrar
 * cursos ajenos. El docente sólo marca "asistió / no asistió"; TARDANZA la
 * deriva el servicio con la hora del servidor.
 */
@RestController
@RequestMapping("/api/v1/docente")
@PreAuthorize("hasRole('DOCENTE')")
public class DocenteAsistenciaController {

    private final AsistenciaService asistenciaService;

    public DocenteAsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    /** Hoja de la clase: todos los alumnos activos, con estado null si no se registró. */
    @GetMapping("/cursos/{cursoId}/asistencia")
    public ResponseEntity<AsistenciaClaseResponse> listarClase(
            @PathVariable Long cursoId,
            @RequestParam Long bloqueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {
        return ResponseEntity.ok(asistenciaService.listarClase(
                authentication.getName(), cursoId, bloqueId, fecha));
    }

    /** Upsert parcial de la hoja de la clase. */
    @PutMapping("/cursos/{cursoId}/asistencia")
    public ResponseEntity<AsistenciaClaseResponse> registrar(
            @PathVariable Long cursoId,
            @Valid @RequestBody AsistenciaBulkRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                asistenciaService.registrar(authentication.getName(), cursoId, datos));
    }
}
