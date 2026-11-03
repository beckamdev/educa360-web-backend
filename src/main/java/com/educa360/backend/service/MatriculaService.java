package com.educa360.backend.service;

import com.educa360.backend.dto.MatriculaEstadoRequest;
import com.educa360.backend.dto.MatriculaRequest;
import com.educa360.backend.dto.MatriculaResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.SeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Matrículas: el estudiante queda inscrito en una sección de un año.
 *
 * Reglas que gobierna (Gestión de matrículas = SECRETARIA, §3):
 * - única por (estudiante, sección) — el unique está en la tabla;
 * - un estudiante no puede tener DOS matrículas en el MISMO año:
 *   ir de 3°A a 3°B es un cambio de sección, no una segunda inscripción;
 * - sólo alumnos habilitados (Estudiante.estado = ACTIVO).
 *
 * "Matrícula vigente" = la del año ABIERTO (el vigente): es la que da
 * sección al alumno para /estudiante/cursos, su ficha y los hijos del
 * apoderado. Sin año abierto no hay vigente.
 *
 * TODO M3: reemplazar el estado PENDIENTE/PAGADO por situacionFinal
 * (EN_CURSO/APROBADO/DESAPROBADO/RETIRADO) y añadir fechaMatricula.
 */
@Service
public class MatriculaService {

    private final MatriculaRepository matriculaRepository;
    private final EstudianteRepository estudianteRepository;
    private final SeccionRepository seccionRepository;
    private final AnioEscolarRepository anioRepository;

    public MatriculaService(MatriculaRepository matriculaRepository,
                            EstudianteRepository estudianteRepository,
                            SeccionRepository seccionRepository,
                            AnioEscolarRepository anioRepository) {
        this.matriculaRepository = matriculaRepository;
        this.estudianteRepository = estudianteRepository;
        this.seccionRepository = seccionRepository;
        this.anioRepository = anioRepository;
    }

    /** Alta desde Secretaría: 404 si no existe el alumno o la sección, 409 por reglas. */
    @Transactional
    public MatriculaResponse crear(MatriculaRequest datos) {
        Estudiante estudiante = estudianteRepository.findById(datos.estudianteId())
                .orElseThrow(() -> new NotFoundException("El estudiante " + datos.estudianteId() + " no existe"));
        Seccion seccion = seccionRepository.findById(datos.seccionId())
                .orElseThrow(() -> new NotFoundException("La sección " + datos.seccionId() + " no existe"));

        if (seccion.getAnio().estaCerrado()) {
            throw new ConflictException("El año " + seccion.getAnio().getAnio()
                    + " está CERRADO: no se pueden crear matrículas");
        }
        if (!estudiante.estaHabilitado()) {
            throw new ConflictException("El estudiante está " + estudiante.getEstado().name()
                    + ": sólo se matriculan alumnos ACTIVOS");
        }
        if (matriculaRepository.existsByEstudianteAndSeccion(estudiante, seccion)) {
            throw new ConflictException("El estudiante ya está matriculado en esa sección");
        }
        if (matriculaRepository.existsByEstudianteIdAndSeccionAnioId(
                estudiante.getId(), seccion.getAnio().getId())) {
            throw new ConflictException("El estudiante ya tiene una matrícula en el año "
                    + seccion.getAnio().getNombre()
                    + ": cambiar de sección dentro del mismo año es un traslado");
        }

        return MatriculaResponse.desde(matriculaRepository.save(new Matricula(estudiante, seccion)));
    }

    /** Cambio de estado (PENDIENTE -> PAGADO es la acción de Secretaría). */
    @Transactional
    public MatriculaResponse cambiarEstado(Long id, MatriculaEstadoRequest datos) {
        Matricula matricula = matriculaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("La matrícula " + id + " no existe"));
        matricula.setEstado(datos.estado());
        return MatriculaResponse.desde(matricula);
    }

    /**
     * La matrícula vigente del alumno: la del año ABIERTO.
     * Sin año abierto (o sin matrícula) → vacío.
     */
    @Transactional(readOnly = true)
    public Optional<Matricula> vigenteDe(Long estudianteId) {
        return anioRepository.findByEstado(AnioEscolar.Estado.ABIERTO)
                .flatMap(anio ->
                        matriculaRepository.findByEstudianteIdAndSeccionAnioId(estudianteId, anio.getId()));
    }
}
