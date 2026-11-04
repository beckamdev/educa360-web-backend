package com.educa360.backend.controller;

import com.educa360.backend.dto.ArchivoDescargaResponse;
import com.educa360.backend.dto.TicketResolverRequest;
import com.educa360.backend.dto.TicketResponse;
import com.educa360.backend.entity.TicketJustificacion;
import com.educa360.backend.service.JustificacionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Bandeja de justificaciones de SECRETARÍA.
 *
 * Secretaría resuelve los tickets PENDIENTE; aprobar convierte las faltas
 * del alcance en JUSTIFICADA en la misma transacción. ADMIN no entra aquí:
 * la resolución es una competencia exclusiva de Secretaría.
 */
@RestController
@RequestMapping("/api/v1/secretaria/tickets")
@PreAuthorize("hasRole('SECRETARIA')")
public class SecretariaTicketController {

    private final JustificacionService justificacionService;

    public SecretariaTicketController(JustificacionService justificacionService) {
        this.justificacionService = justificacionService;
    }

    /** Bandeja filtrable por estado y fecha de inasistencia. */
    @GetMapping
    public ResponseEntity<List<TicketResponse>> bandeja(
            @RequestParam(required = false) TicketJustificacion.Estado estado,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(justificacionService.bandeja(estado, fecha));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(justificacionService.detalle(id));
    }

    /** URL firmada del sustento adjunto, si existe. */
    @GetMapping("/{id}/sustento")
    public ResponseEntity<ArchivoDescargaResponse> sustento(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(justificacionService.urlSustentoSecretaria(
                authentication.getName(), id));
    }

    /** Aprueba o rechaza un ticket PENDIENTE. */
    @PatchMapping("/{id}/resolver")
    public ResponseEntity<TicketResponse> resolver(
            @PathVariable Long id,
            @Valid @RequestBody TicketResolverRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(justificacionService.resolver(
                authentication.getName(), id, datos));
    }
}
