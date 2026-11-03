package com.educa360.backend.dto;

import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Matricula;

/**
 * Un representado del apoderado: su ficha + su sección del año vigente.
 *
 * Sección/matrícula pueden venir null (aún sin matrícula): el hijo existe
 * como vínculo antes de ser inscrito.
 */
public record HijoResponse(
        Long estudianteId,
        String nombres,
        String apellidos,
        String dni,
        String email,
        String seccionEtiqueta, // del año ABIERTO; null si no está matriculado
        String anioNombre,
        Matricula.Estado matriculaEstado,
        ApoderadoEstudiante.Parentesco parentesco,
        boolean responsablePrincipal
) {
}
