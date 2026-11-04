package com.educa360.backend.controller;

import com.educa360.backend.dto.MaterialPublicacionRequest;
import com.educa360.backend.dto.MaterialResponse;
import com.educa360.backend.dto.MaterialUpdateRequest;
import com.educa360.backend.service.MaterialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Materiales de los cursos del DOCENTE autenticado.
 *
 * El docente no pasa su id: sale del token. Un curso o material ajeno
 * responde 404 para no revelar que existe.
 */
@RestController
@RequestMapping("/api/v1/docente")
@PreAuthorize("hasRole('DOCENTE')")
public class DocenteMaterialController {

    private final MaterialService materialService;

    public DocenteMaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/cursos/{cursoId}/materiales")
    public ResponseEntity<List<MaterialResponse>> listar(
            @PathVariable Long cursoId, Authentication authentication) {
        return ResponseEntity.ok(
                materialService.listarDelCurso(authentication.getName(), cursoId));
    }

    @PostMapping(value = "/cursos/{cursoId}/materiales",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialResponse> crear(
            @PathVariable Long cursoId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("titulo") String titulo,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam(value = "contenidoId", required = false) Long contenidoId,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                materialService.crear(authentication.getName(), cursoId, titulo,
                        descripcion, contenidoId, archivo));
    }

    @PutMapping("/materiales/{id}")
    public ResponseEntity<MaterialResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody MaterialUpdateRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(
                materialService.actualizar(authentication.getName(), id, datos));
    }

    @PutMapping(value = "/materiales/{id}/archivo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialResponse> reemplazarArchivo(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {
        return ResponseEntity.ok(
                materialService.reemplazarArchivo(authentication.getName(), id, archivo));
    }

    /** {"publicado":true|false}. */
    @PatchMapping("/materiales/{id}/publicacion")
    public ResponseEntity<MaterialResponse> cambiarPublicacion(
            @PathVariable Long id,
            @Valid @RequestBody MaterialPublicacionRequest datos,
            Authentication authentication) {
        return ResponseEntity.ok(materialService.cambiarPublicacion(
                authentication.getName(), id, datos.publicado()));
    }

    @DeleteMapping("/materiales/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
                                         Authentication authentication) {
        materialService.eliminar(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
