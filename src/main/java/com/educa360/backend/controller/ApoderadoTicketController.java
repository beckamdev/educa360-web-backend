package com.educa360.backend.controller;

import com.educa360.backend.dto.TicketCreateRequest;
import com.educa360.backend.dto.TicketResponse;
import com.educa360.backend.entity.TicketJustificacion;
import com.educa360.backend.service.JustificacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tickets de justificación de un HIJO del apoderado autenticado.
 *
 * El vínculo lo crea Secretaría; si el hijo no está vinculado o no está
 * activo, responde 404. El apoderado sólo crea y consulta: resolver es
 * competencia de Secretaría.
 */
@RestController
@RequestMapping("/api/v1/apoderado/hijos")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoTicketController {

    private final JustificacionService justificacionService;

    public ApoderadoTicketController(JustificacionService justificacionService) {
        this.justificacionService = justificacionService;
    }

    /** Historial de tickets del hijo, filtrable por año y estado. */
    @GetMapping("/{hijoId}/tickets")
    public ResponseEntity<List<TicketResponse>> listar(
            @PathVariable Long hijoId,
            @RequestParam(required = false) Long anioId,
            @RequestParam(required = false) TicketJustificacion.Estado estado,
            Authentication authentication) {
        return ResponseEntity.ok(justificacionService.listarDelHijo(
                authentication.getName(), hijoId, anioId, estado));
    }

    /** Crea un ticket PENDIENTE; el alumno sale de la ruta y el apoderado del token. */
    @PostMapping("/{hijoId}/tickets")
    public ResponseEntity<TicketResponse> crear(
            @PathVariable Long hijoId,
            @Valid @RequestBody TicketCreateRequest datos,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                justificacionService.crear(authentication.getName(), hijoId, datos));
    }
}
