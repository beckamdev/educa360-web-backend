package com.educa360.backend.service;

import com.educa360.backend.dto.CambioSeccionRequest;
import com.educa360.backend.dto.MatriculaRequest;
import com.educa360.backend.dto.MatriculaResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.AsistenciaRepository;
import com.educa360.backend.repository.CalificacionRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.SeccionRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Matrículas: el estudiante queda inscrito en una sección de un año.
 *
 * Reglas que gobierna (gestión de matrículas = SECRETARIA, §3):
 * - única por (estudiante, año) — el unique está en la tabla;
 * - un estudiante no puede tener DOS matrículas en el MISMO año:
 *   ir de 3°A a 3°B es un cambio de sección, no una segunda inscripción;
 * - sólo alumnos habilitados (Estudiante.estado = ACTIVO);
 * - el cambio de sección sólo se permite antes de AnioEscolar.fechaInicio
 *   y mientras no exista asistencia ni calificación;
 * - el retiro marca la matrícula RETIRADO y al estudiante RETIRADO.
 *
 * "Matrícula vigente" = la del año ABIERTO (el vigente): es la que da
 * sección al alumno para /estudiante/cursos, su ficha y los hijos del
 * apoderado. Sin año abierto no hay vigente.
 */
@Service
public class MatriculaService {

    private final MatriculaRepository matriculaRepository;
    private final EstudianteRepository estudianteRepository;
    private final SeccionRepository seccionRepository;
    private final AnioEscolarRepository anioRepository;
    private final UserRepository userRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final CalificacionRepository calificacionRepository;
    private final Clock clock;

    @Autowired
    public MatriculaService(MatriculaRepository matriculaRepository,
                            EstudianteRepository estudianteRepository,
                            SeccionRepository seccionRepository,
                            AnioEscolarRepository anioRepository,
                            UserRepository userRepository,
                            AsistenciaRepository asistenciaRepository,
                            CalificacionRepository calificacionRepository) {
        this(matriculaRepository, estudianteRepository, seccionRepository,
                anioRepository, userRepository, asistenciaRepository,
                calificacionRepository, Clock.systemDefaultZone());
    }

    /** Visible para tests: inyecta un reloj falso. */
    MatriculaService(MatriculaRepository matriculaRepository,
                     EstudianteRepository estudianteRepository,
                     SeccionRepository seccionRepository,
                     AnioEscolarRepository anioRepository,
                     UserRepository userRepository,
                     AsistenciaRepository asistenciaRepository,
                     CalificacionRepository calificacionRepository,
                     Clock clock) {
        this.matriculaRepository = matriculaRepository;
        this.estudianteRepository = estudianteRepository;
        this.seccionRepository = seccionRepository;
        this.anioRepository = anioRepository;
        this.userRepository = userRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.calificacionRepository = calificacionRepository;
        this.clock = clock;
    }

    /** Alta desde Secretaría: 404 si no existe el alumno o la sección, 409 por reglas. */
    @Transactional
    public MatriculaResponse crear(MatriculaRequest datos) {
        Estudiante estudiante = estudianteRepository.findById(datos.estudianteId())
                .orElseThrow(() -> new NotFoundException("El estudiante " + datos.estudianteId() + " no existe"));
        Seccion seccion = seccionRepository.findById(datos.seccionId())
                .orElseThrow(() -> new NotFoundException("La sección " + datos.seccionId() + " no existe"));
        AnioEscolar anio = seccion.getAnio();

        if (anio.estaCerrado()) {
            throw new ConflictException("El año " + anio.getAnio()
                    + " está CERRADO: no se pueden crear matrículas");
        }
        if (!estudiante.estaHabilitado()) {
            throw new ConflictException("El estudiante está " + estudiante.getEstado().name()
                    + ": sólo se matriculan alumnos ACTIVOS");
        }
        if (matriculaRepository.existsByEstudianteIdAndAnioId(estudiante.getId(), anio.getId())) {
            throw new ConflictException("El estudiante ya tiene una matrícula en el año "
                    + anio.getNombre()
                    + ": cambiar de sección dentro del mismo año es un traslado");
        }

        Matricula matricula = new Matricula(estudiante, anio, seccion, datos.fechaMatricula());
        matricula.setObservacion(datos.observacion());
        return MatriculaResponse.desde(matriculaRepository.save(matricula));
    }

    /**
     * Cambio de sección: sólo antes del primer día de clases y sin actividad.
     *
     * La sección nueva debe ser del mismo año. Si es la misma, no hay cambio.
     */
    @Transactional
    public MatriculaResponse cambiarSeccion(Long id, CambioSeccionRequest datos) {
        Matricula matricula = buscarEntidad(id);
        AnioEscolar anio = matricula.getAnio();

        if (anio.estaCerrado()) {
            throw new ConflictException("El año " + anio.getAnio()
                    + " está CERRADO: no se puede cambiar de sección");
        }
        if (matricula.estaRetirada()) {
            throw new ConflictException("La matrícula está RETIRADA: no se puede cambiar de sección");
        }
        if (!LocalDate.now(clock).isBefore(anio.getFechaInicio())) {
            throw new ConflictException("El año " + anio.getAnio()
                    + " ya inició clases el " + anio.getFechaInicio()
                    + ": la sección no se puede cambiar");
        }
        if (tieneActividad(matricula)) {
            throw new ConflictException("La matrícula ya tiene asistencias o calificaciones: "
                    + "la sección no se puede cambiar");
        }

        Seccion nueva = seccionRepository.findById(datos.seccionId())
                .orElseThrow(() -> new NotFoundException("La sección " + datos.seccionId() + " no existe"));
        if (!nueva.getAnio().getId().equals(anio.getId())) {
            throw new ConflictException("La sección " + nueva.getEtiqueta()
                    + " pertenece a otro año escolar");
        }
        if (nueva.getId().equals(matricula.getSeccion().getId())) {
            return MatriculaResponse.desde(matricula);
        }

        matricula.setSeccion(nueva);
        return MatriculaResponse.desde(matricula);
    }

    /** Retiro del año: la matrícula y el estudiante quedan RETIRADOS. */
    @Transactional
    public MatriculaResponse retirar(Long id) {
        Matricula matricula = buscarEntidad(id);
        if (matricula.getAnio().estaCerrado()) {
            throw new ConflictException("El año " + matricula.getAnio().getAnio()
                    + " está CERRADO: no se puede retirar");
        }
        if (matricula.estaRetirada()) {
            return MatriculaResponse.desde(matricula);
        }

        matricula.setSituacionFinal(Matricula.SituacionFinal.RETIRADO);
        matricula.getEstudiante().setEstado(Estudiante.EstadoEstudiante.RETIRADO);
        return MatriculaResponse.desde(matricula);
    }

    /** Listado de Secretaría: filtra por año, sección y texto (nombre/DNI/email). */
    @Transactional(readOnly = true)
    public List<MatriculaResponse> listar(Long anioId, Long seccionId, String q) {
        List<Matricula> matriculas;
        if (seccionId != null) {
            matriculas = matriculaRepository.findBySeccionId(seccionId);
        } else if (anioId != null) {
            matriculas = matriculaRepository.findByAnioId(anioId);
        } else {
            matriculas = matriculaRepository.findAll();
        }
        return matriculas.stream()
                .filter(matricula -> q == null || q.isBlank() || coincide(matricula.getEstudiante().getUser(), q))
                .map(MatriculaResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public MatriculaResponse buscar(Long id) {
        return MatriculaResponse.desde(buscarEntidad(id));
    }

    /**
     * La matrícula vigente del alumno: la del año ABIERTO.
     * Sin año abierto (o sin matrícula) → vacío.
     */
    @Transactional(readOnly = true)
    public Optional<Matricula> vigenteDe(Long estudianteId) {
        return anioRepository.findByEstado(AnioEscolar.Estado.ABIERTO)
                .flatMap(anio -> matriculaRepository.findByEstudianteIdAndAnioId(estudianteId, anio.getId()));
    }

    /** La matrícula vigente del usuario autenticado (zona /estudiante). */
    @Transactional(readOnly = true)
    public Optional<MatriculaResponse> vigenteDelUsuario(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Estudiante estudiante = estudianteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Estudiante"));
        return vigenteDe(estudiante.getId()).map(MatriculaResponse::desde);
    }

    @Transactional(readOnly = true)
    public Matricula buscarEntidad(Long id) {
        return matriculaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("La matrícula " + id + " no existe"));
    }

    private boolean coincide(User user, String q) {
        String texto = q.toLowerCase(Locale.ROOT);
        return (user.getEmail() != null && user.getEmail().toLowerCase(Locale.ROOT).contains(texto))
                || (user.getNombres() != null && user.getNombres().toLowerCase(Locale.ROOT).contains(texto))
                || (user.getApellidos() != null && user.getApellidos().toLowerCase(Locale.ROOT).contains(texto))
                || (user.getDni() != null && user.getDni().contains(q));
    }

    /**
     * ¿La matrícula ya tiene actividad académica?
     *
     * M5: asistencias. M6: calificaciones. Si existe cualquiera de las dos,
     * la sección ya no se puede cambiar.
     */
    private boolean tieneActividad(Matricula matricula) {
        return asistenciaRepository.existsByMatriculaId(matricula.getId())
                || calificacionRepository.existsByMatriculaId(matricula.getId());
    }
}
