package com.educa360.backend.dto;

import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;

import java.time.LocalDate;
import java.util.List;

/**
 * Ficha del alumno para los listados de Secretaría (§4/§5).
 *
 * Nace aquí, NO reutiliza UsuarioResponse: esa es la vista de cuentas de
 * ADMIN; ésta es la de alumnos con su sección, matrícula y apoderados.
 *
 * Sección y matrícula son las del año ABIERTO (el vigente); null si el
 * alumno aún no está matriculado.
 */
public record EstudianteResponse(
        Long id, // id del perfil
        String email,
        String nombres,
        String apellidos,
        String dni,
        LocalDate fechaNacimiento,
        Estudiante.EstadoEstudiante estado,
        boolean activo,
        String seccionEtiqueta,
        String anioNombre,
        LocalDate fechaMatricula,
        Matricula.SituacionFinal situacionFinal,
        List<Vinculo> apoderados
) {
    /** Apoderado vinculado y su parentesco (sólo vínculos activos). */
    public record Vinculo(
            Long id,
            String nombres,
            ApoderadoEstudiante.Parentesco parentesco,
            boolean responsablePrincipal
    ) {
    }
}
