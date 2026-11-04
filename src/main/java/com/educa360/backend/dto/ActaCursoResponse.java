package com.educa360.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Acta de un curso en un bimestre: una fila por alumno y una columna por
 * unidad, más el promedio del bimestre.
 */
public record ActaCursoResponse(
        Long cursoId,
        String materiaNombre,
        String seccionEtiqueta,
        Long bimestreId,
        String bimestreNombre,
        List<ActaAlumnoResponse> alumnos,
        BigDecimal promedioCurso
) {
}
