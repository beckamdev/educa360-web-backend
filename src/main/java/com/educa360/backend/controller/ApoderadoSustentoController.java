package com.educa360.backend.controller;

import com.educa360.backend.dto.ArchivoDescargaResponse;
import com.educa360.backend.dto.TicketResponse;
import com.educa360.backend.service.JustificacionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Sustento de un ticket propio del APODERADO. Sólo se adjunta o reemplaza
 * mientras el ticket está PENDIENTE.
 */
@RestController
@RequestMapping("/api/v1/apoderado/tickets")
@PreAuthorize("hasRole('APODERADO')")
public class ApoderadoSustentoController {

    private final JustificacionService justificacionService;

    public ApoderadoSustentoController(JustificacionService justificacionService) {
        this.justificacionService = justificacionService;
    }

    @PostMapping(value = "/{ticketId}/sustento",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketResponse> subir(
            @PathVariable Long ticketId,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {
        return ResponseEntity.ok(justificacionService.subirSustento(
                authentication.getName(), ticketId, archivo));
    }

    @GetMapping("/{ticketId}/sustento")
    public ResponseEntity<ArchivoDescargaResponse> descargar(
            @PathVariable Long ticketId, Authentication authentication) {
        return ResponseEntity.ok(justificacionService.urlSustentoApoderado(
                authentication.getName(), ticketId));
    }
}
