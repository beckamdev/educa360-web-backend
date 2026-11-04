package com.educa360.backend.service;

import com.educa360.backend.dto.AsistenciaAlumnoResponse;
import com.educa360.backend.dto.AsistenciaBulkRequest;
import com.educa360.backend.dto.AsistenciaClaseResponse;
import com.educa360.backend.dto.AsistenciaDetalleResponse;
import com.educa360.backend.dto.AsistenciaItemRequest;
import com.educa360.backend.dto.AsistenciaResumenResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.HorarioBloque;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.TicketJustificacion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.AsistenciaRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.HorarioBloqueRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.TicketJustificacionRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asistencia por bloque de horario y fecha (Módulo 5).
 *
 * Reglas que gobierna:
 * - el bloque debe pertenecer a un curso de la sección de la matrícula;
 * - la fecha debe caer en el día del bloque y dentro de un bimestre ABIERTO;
 * - no se registra asistencia futura;
 * - sólo el docente dueño del curso registra o corrige;
 * - el docente sólo marca "asistió / no asistió": TARDANZA se deriva con la
 *   hora del servidor (más de 15 minutos después del inicio del bloque);
 * - JUSTIFICADA sólo nace de un ticket aprobado: si el ticket ya existe al
 *   registrar la falta, la fila se crea justificada;
 * - no se elimina: se corrige y queda la auditoría de {@link Asistencia}.
 *
 * El porcentaje se calcula sobre asistencias registradas; son efectivas
 * PRESENTE, TARDANZA y JUSTIFICADA. Sólo FALTA es inasistencia injustificada.
 */
@Service
public class AsistenciaService {

    /** Margen para considerar que un alumno llegó tarde. */
    static final int MINUTOS_TARDANZA = 15;

    private final AsistenciaRepository asistenciaRepository;
    private final CursoRepository cursoRepository;
    private final HorarioBloqueRepository horarioRepository;
    private final BimestreRepository bimestreRepository;
    private final DocenteRepository docenteRepository;
    private final UserRepository userRepository;
    private final EstudianteRepository estudianteRepository;
    private final MatriculaRepository matriculaRepository;
    private final MatriculaService matriculaService;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final TicketJustificacionRepository ticketRepository;
    private final Clock clock;

    @Autowired
    public AsistenciaService(AsistenciaRepository asistenciaRepository,
                             CursoRepository cursoRepository,
                             HorarioBloqueRepository horarioRepository,
                             BimestreRepository bimestreRepository,
                             DocenteRepository docenteRepository,
                             UserRepository userRepository,
                             EstudianteRepository estudianteRepository,
                             MatriculaRepository matriculaRepository,
                             MatriculaService matriculaService,
                             ApoderadoRepository apoderadoRepository,
                             ApoderadoEstudianteRepository vinculoRepository,
                             TicketJustificacionRepository ticketRepository) {
        this(asistenciaRepository, cursoRepository, horarioRepository, bimestreRepository,
                docenteRepository, userRepository, estudianteRepository, matriculaRepository,
                matriculaService, apoderadoRepository, vinculoRepository, ticketRepository,
                Clock.systemDefaultZone());
    }

    /** Visible para tests: inyecta un reloj falso. */
    AsistenciaService(AsistenciaRepository asistenciaRepository,
                      CursoRepository cursoRepository,
                      HorarioBloqueRepository horarioRepository,
                      BimestreRepository bimestreRepository,
                      DocenteRepository docenteRepository,
                      UserRepository userRepository,
                      EstudianteRepository estudianteRepository,
                      MatriculaRepository matriculaRepository,
                      MatriculaService matriculaService,
                      ApoderadoRepository apoderadoRepository,
                      ApoderadoEstudianteRepository vinculoRepository,
                      TicketJustificacionRepository ticketRepository,
                      Clock clock) {
        this.asistenciaRepository = asistenciaRepository;
        this.cursoRepository = cursoRepository;
        this.horarioRepository = horarioRepository;
        this.bimestreRepository = bimestreRepository;
        this.docenteRepository = docenteRepository;
        this.userRepository = userRepository;
        this.estudianteRepository = estudianteRepository;
        this.matriculaRepository = matriculaRepository;
        this.matriculaService = matriculaService;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    // ==================== Docente: hoja de clase ====================

    @Transactional(readOnly = true)
    public AsistenciaClaseResponse listarClase(String email, Long cursoId,
                                               Long bloqueId, LocalDate fecha) {
        Docente docente = docenteDe(email);
        Curso curso = cursoDelDocente(docente, cursoId);
        return claseDe(curso, bloqueId, fecha);
    }

    @Transactional
    public AsistenciaClaseResponse registrar(String email, Long cursoId,
                                             AsistenciaBulkRequest datos) {
        Docente docente = docenteDe(email);
        Curso curso = cursoDelDocente(docente, cursoId);
        HorarioBloque bloque = bloqueDelCurso(curso, datos.bloqueId());
        LocalDate fecha = datos.fecha();

        validarNoFutura(fecha);
        validarDia(bloque, fecha);
        validarBimestreAbierto(curso, fecha);

        List<AsistenciaItemRequest> items = datos.asistencias();
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("La lista de asistencias no puede estar vacía");
        }
        Set<Long> matriculaIds = new HashSet<>();
        for (AsistenciaItemRequest item : items) {
            if (item.matriculaId() == null) {
                throw new BadRequestException("La matrícula es obligatoria");
            }
            if (item.asistio() == null) {
                throw new BadRequestException(
                        "Debes indicar si el alumno asistió (matrícula " + item.matriculaId() + ")");
            }
            if (!matriculaIds.add(item.matriculaId())) {
                throw new BadRequestException(
                        "La lista no puede repetir la matrícula " + item.matriculaId());
            }
        }

        for (AsistenciaItemRequest item : items) {
            Matricula matricula = matriculaRepository.findById(item.matriculaId())
                    .orElseThrow(() -> new NotFoundException(
                            "La matrícula " + item.matriculaId() + " no existe"));
            validarMatriculaDelCurso(matricula, curso);

            Asistencia existente = asistenciaRepository
                    .findByMatriculaIdAndBloqueIdAndFecha(
                            matricula.getId(), bloque.getId(), fecha)
                    .orElse(null);
            aplicar(existente, matricula, bloque, fecha, item, docente);
        }

        asistenciaRepository.flush();
        return construirClase(curso, bloque, fecha);
    }

    @Transactional(readOnly = true)
    public AsistenciaClaseResponse listarClaseAdmin(Long cursoId, Long bloqueId, LocalDate fecha) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new NotFoundException("El curso " + cursoId + " no existe"));
        return claseDe(curso, bloqueId, fecha);
    }

    // ==================== Historial del alumno ====================

    @Transactional(readOnly = true)
    public AsistenciaResumenResponse resumenDelEstudiante(String email, Long anioId,
                                                          Long bimestreId) {
        Estudiante estudiante = estudianteDe(email);
        Matricula matricula = matriculaDe(estudiante, anioId);
        return resumenDe(matricula, bimestreId);
    }

    @Transactional(readOnly = true)
    public AsistenciaResumenResponse resumenDelHijo(String email, Long hijoId, Long anioId,
                                                    Long bimestreId) {
        Apoderado apoderado = apoderadoDe(email);
        ApoderadoEstudiante vinculo = vinculoRepository
                .findByApoderadoIdAndEstudianteId(apoderado.getId(), hijoId)
                .filter(ApoderadoEstudiante::isActivo)
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante " + hijoId + " no está vinculado a este apoderado"));

        Estudiante estudiante = vinculo.getEstudiante();
        if (!estudiante.estaHabilitado()) {
            throw new NotFoundException("El estudiante no está activo");
        }
        Matricula matricula = matriculaDe(estudiante, anioId);
        return resumenDe(matricula, bimestreId);
    }

    @Transactional(readOnly = true)
    public AsistenciaResumenResponse resumenDeEstudiante(Long estudianteId, Long anioId,
                                                         Long bimestreId) {
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante " + estudianteId + " no existe"));
        Matricula matricula = matriculaDe(estudiante, anioId);
        return resumenDe(matricula, bimestreId);
    }

    // ==================== Helpers: clase ====================

    private AsistenciaClaseResponse claseDe(Curso curso, Long bloqueId, LocalDate fecha) {
        HorarioBloque bloque = bloqueDelCurso(curso, bloqueId);
        validarDia(bloque, fecha);
        return construirClase(curso, bloque, fecha);
    }

    private AsistenciaClaseResponse construirClase(Curso curso, HorarioBloque bloque,
                                                   LocalDate fecha) {
        Map<Long, Asistencia> porMatricula = asistenciaRepository
                .findByBloqueIdAndFecha(bloque.getId(), fecha).stream()
                .collect(Collectors.toMap(a -> a.getMatricula().getId(), Function.identity()));

        List<AsistenciaAlumnoResponse> filas = matriculaRepository
                .findBySeccionId(curso.getSeccion().getId()).stream()
                .filter(matricula -> !matricula.estaRetirada())
                .sorted(Comparator.comparing(this::nombreDe))
                .map(matricula -> AsistenciaAlumnoResponse.desde(
                        matricula, porMatricula.get(matricula.getId())))
                .toList();

        return AsistenciaClaseResponse.desde(curso, bloque, fecha, filas);
    }

    private HorarioBloque bloqueDelCurso(Curso curso, Long bloqueId) {
        HorarioBloque bloque = horarioRepository.findById(bloqueId)
                .orElseThrow(() -> new NotFoundException(
                        "El bloque " + bloqueId + " no existe"));
        if (!bloque.getCurso().getId().equals(curso.getId())) {
            // 404 a propósito: no revelar bloques de cursos ajenos.
            throw new NotFoundException("El bloque " + bloqueId + " no existe");
        }
        return bloque;
    }

    private void validarDia(HorarioBloque bloque, LocalDate fecha) {
        DiaSemana esperado = diaDe(fecha);
        if (esperado == null || esperado != bloque.getDia()) {
            throw new BadRequestException("La fecha " + fecha + " no corresponde al "
                    + bloque.getDia() + " del bloque");
        }
    }

    private void validarNoFutura(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now(clock))) {
            throw new BadRequestException(
                    "No se puede registrar asistencia de una fecha futura: " + fecha);
        }
    }

    private Bimestre validarBimestreAbierto(Curso curso, LocalDate fecha) {
        AnioEscolar anio = curso.getSeccion().getAnio();
        if (anio.estaCerrado()) {
            throw new ConflictException("El año " + anio.getAnio()
                    + " está CERRADO: no se registra asistencia");
        }

        Bimestre bimestre = bimestreRepository.findByAnioIdOrderByNumeroAsc(anio.getId()).stream()
                .filter(b -> !fecha.isBefore(b.getFechaInicio())
                        && !fecha.isAfter(b.getFechaFin()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("La fecha " + fecha
                        + " no pertenece a ningún bimestre del año " + anio.getAnio()));

        if (bimestre.getEstado() != Bimestre.Estado.ABIERTO) {
            throw new ConflictException("El " + bimestre.getNombre()
                    + " no está ABIERTO: no se registra asistencia");
        }
        return bimestre;
    }

    private void validarMatriculaDelCurso(Matricula matricula, Curso curso) {
        if (!matricula.getSeccion().getId().equals(curso.getSeccion().getId())) {
            throw new NotFoundException("La matrícula " + matricula.getId()
                    + " no pertenece a este curso");
        }
        if (matricula.estaRetirada()) {
            throw new ConflictException("La matrícula " + matricula.getId()
                    + " está RETIRADA: no se registra asistencia");
        }
        if (!matricula.getEstudiante().estaHabilitado()) {
            throw new ConflictException("El estudiante de la matrícula " + matricula.getId()
                    + " no está ACTIVO");
        }
    }

    /**
     * Upsert de una fila. Si ya estaba JUSTIFICADA, el docente no la pisa:
     * esa decisión es de Secretaría al aprobar el ticket (M7).
     */
    private void aplicar(Asistencia existente, Matricula matricula, HorarioBloque bloque,
                         LocalDate fecha, AsistenciaItemRequest item, Docente docente) {
        if (existente != null && existente.estaJustificada()) {
            throw new ConflictException("La asistencia de la matrícula " + matricula.getId()
                    + " ya está JUSTIFICADA por un ticket aprobado");
        }

        Asistencia asistencia = existente != null
                ? existente
                : new Asistencia(matricula, bloque, fecha);

        if (!item.asistio()) {
            TicketJustificacion ticket = ticketAprobado(matricula, bloque, fecha);
            if (ticket != null) {
                // La falta ya estaba justificada por Secretaría: nace o se
                // corrige directamente como JUSTIFICADA y queda trazada.
                asistencia.setEstado(Asistencia.Estado.JUSTIFICADA);
                asistencia.setTicket(ticket);
            } else {
                asistencia.setEstado(Asistencia.Estado.FALTA);
                asistencia.setTicket(null);
            }
        } else {
            if (existente == null || existente.getEstado() == Asistencia.Estado.FALTA) {
                // Sólo se deriva al crear o al corregir una falta; una fila ya
                // marcada PRESENTE/TARDANZA no se degrada por una edición tardía.
                asistencia.setEstado(estadoPresente(bloque, fecha));
            }
            asistencia.setTicket(null);
        }

        asistencia.setObservacion(item.observacion());
        asistencia.setRegistradoPor(docente);
        asistenciaRepository.save(asistencia);
    }

    /** Ticket aprobado que cubre la falta de esta matrícula, bloque y fecha. */
    private TicketJustificacion ticketAprobado(Matricula matricula, HorarioBloque bloque,
                                               LocalDate fecha) {
        return ticketRepository.buscarCoberturaAprobada(
                        matricula.getEstudiante().getId(), fecha, bloque.getId())
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * PRESENTE o TARDANZA según la hora del servidor.
     *
     * Para fechas pasadas no hay forma de reconstruir la hora de llegada, así
     * que se asume PRESENTE. La tardanza sólo se evalúa el mismo día del bloque.
     */
    private Asistencia.Estado estadoPresente(HorarioBloque bloque, LocalDate fecha) {
        LocalDate hoy = LocalDate.now(clock);
        if (fecha.isBefore(hoy)) {
            return Asistencia.Estado.PRESENTE;
        }
        LocalTime limite = bloque.getHoraInicio().plusMinutes(MINUTOS_TARDANZA);
        return LocalTime.now(clock).isAfter(limite)
                ? Asistencia.Estado.TARDANZA
                : Asistencia.Estado.PRESENTE;
    }

    private DiaSemana diaDe(LocalDate fecha) {
        return switch (fecha.getDayOfWeek()) {
            case MONDAY -> DiaSemana.LUNES;
            case TUESDAY -> DiaSemana.MARTES;
            case WEDNESDAY -> DiaSemana.MIERCOLES;
            case THURSDAY -> DiaSemana.JUEVES;
            case FRIDAY -> DiaSemana.VIERNES;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> null;
        };
    }

    // ==================== Helpers: historial ====================

    private AsistenciaResumenResponse resumenDe(Matricula matricula, Long bimestreId) {
        Bimestre bimestre = null;
        List<Asistencia> asistencias;

        if (bimestreId != null) {
            bimestre = bimestreRepository.findById(bimestreId)
                    .orElseThrow(() -> new NotFoundException(
                            "El bimestre " + bimestreId + " no existe"));
            if (!bimestre.getAnio().getId().equals(matricula.getAnio().getId())) {
                throw new ConflictException("El " + bimestre.getNombre()
                        + " no pertenece al año de la matrícula");
            }
            asistencias = asistenciaRepository
                    .findByMatriculaIdAndFechaBetweenOrderByFechaAscBloqueHoraInicioAsc(
                            matricula.getId(), bimestre.getFechaInicio(), bimestre.getFechaFin());
        } else {
            asistencias = asistenciaRepository
                    .findByMatriculaIdOrderByFechaAscBloqueHoraInicioAsc(matricula.getId());
        }

        int presentes = contar(asistencias, Asistencia.Estado.PRESENTE);
        int tardanzas = contar(asistencias, Asistencia.Estado.TARDANZA);
        int justificadas = contar(asistencias, Asistencia.Estado.JUSTIFICADA);
        int faltas = contar(asistencias, Asistencia.Estado.FALTA);
        int efectivas = presentes + tardanzas + justificadas;
        int total = asistencias.size();

        BigDecimal porcentaje = total == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(efectivas)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        List<AsistenciaDetalleResponse> detalle = asistencias.stream()
                .map(AsistenciaDetalleResponse::desde)
                .toList();

        User user = matricula.getEstudiante().getUser();
        return new AsistenciaResumenResponse(
                matricula.getId(),
                matricula.getEstudiante().getId(),
                user.getNombres() + " " + user.getApellidos(),
                user.getDni(),
                matricula.getSeccion().getEtiqueta(),
                matricula.getAnio().getId(),
                matricula.getAnio().getAnio(),
                bimestre != null ? bimestre.getId() : null,
                bimestre != null ? bimestre.getNombre() : null,
                total,
                presentes,
                tardanzas,
                justificadas,
                faltas,
                efectivas,
                porcentaje,
                detalle
        );
    }

    private int contar(List<Asistencia> asistencias, Asistencia.Estado estado) {
        return (int) asistencias.stream()
                .filter(asistencia -> asistencia.getEstado() == estado)
                .count();
    }

    private Matricula matriculaDe(Estudiante estudiante, Long anioId) {
        if (anioId != null) {
            return matriculaRepository.findByEstudianteIdAndAnioId(estudiante.getId(), anioId)
                    .orElseThrow(() -> new NotFoundException("El estudiante "
                            + estudiante.getId() + " no tiene matrícula en el año " + anioId));
        }
        return matriculaService.vigenteDe(estudiante.getId())
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante no tiene matrícula en el año vigente"));
    }

    private String nombreDe(Matricula matricula) {
        User user = matricula.getEstudiante().getUser();
        return user.getNombres() + " " + user.getApellidos();
    }

    // ==================== Helpers: identidad ====================

    private Docente docenteDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return docenteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Docente"));
    }

    private Curso cursoDelDocente(Docente docente, Long cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new NotFoundException("El curso " + cursoId + " no existe"));
        if (!curso.getDocente().getId().equals(docente.getId())) {
            throw new NotFoundException("El curso " + cursoId + " no existe");
        }
        return curso;
    }

    private Estudiante estudianteDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Estudiante estudiante = estudianteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Estudiante"));
        if (!estudiante.estaHabilitado()) {
            throw new NotFoundException("El estudiante no está activo");
        }
        return estudiante;
    }

    private Apoderado apoderadoDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return apoderadoRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Apoderado"));
    }
}
