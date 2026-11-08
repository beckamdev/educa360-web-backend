package com.educa360.backend.dto;

import com.educa360.backend.entity.ApoderadoEstudiante;

/**
 * Resultado del alta de familia.
 *
 * Devuelve los ids de PERFIL (no los de la cuenta), que son los que necesitan
 * la matrícula y el resto de endpoints de Secretaría.
 */
public record FamiliaResponse(
        Long apoderadoId,
        String apoderadoNombres,
        String apoderadoDni,
        boolean apoderadoCreado,
        Long estudianteId,
        String estudianteNombres,
        String estudianteDni,
        ApoderadoEstudiante.Parentesco parentesco,
        boolean responsablePrincipal
) {
}
