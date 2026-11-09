package com.educa360.backend.controller;

import com.educa360.backend.dto.BimestreResponse;
import com.educa360.backend.service.BimestreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catálogo de bimestres de SOLO LECTURA para todos los roles.
 *
 * La gestión (crear, editar, abrir/cerrar) sigue siendo exclusiva de ADMIN en
 * {@link BimestreController}. Este endpoint existe porque el docente necesita
 * el id del bimestre para crear su primera unidad: sus propios endpoints solo
 * devuelven las unidades que ya existen, así que sin esto no podría arrancar
 * la planificación de un bimestre nuevo.
 *
 * Devuelve lo mismo que el listado de ADMIN ({@link BimestreResponse}), con el
 * estado incluido para que el frontend deshabilite los bimestres CERRADOS.
 */
@RestController
@RequestMapping("/api/v1/bimestres")
@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DOCENTE', 'ESTUDIANTE', 'APODERADO')")
public class BimestreConsultaController {

    private final BimestreService bimestreService;

    public BimestreConsultaController(BimestreService bimestreService) {
        this.bimestreService = bimestreService;
    }

    /** Catálogo completo (?anioId= para acotar a un año; 404 si no existe). */
    @GetMapping
    public ResponseEntity<List<BimestreResponse>> listar(
            @RequestParam(required = false) Long anioId) {
        return ResponseEntity.ok(bimestreService.catalogo(anioId));
    }
}
