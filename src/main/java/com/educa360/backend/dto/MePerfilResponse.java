package com.educa360.backend.dto;

import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Matricula;

import java.util.List;

/**
 * Perfil del rol dentro de /auth/me: lo que cada pantalla necesita sin N llamadas.
 *
 * Cada rol pinta lo suyo y el resto queda null:
 * - DOCENTE: título y materias habilitadas (Módulo 2);
 * - ESTUDIANTE: su sección del año vigente (Módulo 3);
 * - APODERADO: sus hijos (Módulo 3);
 * - ADMIN/SECRETARIA: null.
 */
public record MePerfilResponse(
        String titulo,                      // DOCENTE
        List<MateriaResponse> materias,     // DOCENTE
        String seccionEtiqueta,             // ESTUDIANTE
        String anioNombre,                  // ESTUDIANTE
        Matricula.SituacionFinal situacionFinal, // ESTUDIANTE
        List<HijoResponse> hijos            // APODERADO
) {
    public static MePerfilResponse docente(String titulo, List<MateriaResponse> materias) {
        return new MePerfilResponse(titulo, materias, null, null, null, null);
    }

    public static MePerfilResponse estudiante(String seccionEtiqueta, String anioNombre,
                                              Matricula.SituacionFinal situacionFinal) {
        return new MePerfilResponse(null, null, seccionEtiqueta, anioNombre, situacionFinal, null);
    }

    public static MePerfilResponse apoderado(List<HijoResponse> hijos) {
        return new MePerfilResponse(null, null, null, null, null, hijos);
    }
}
