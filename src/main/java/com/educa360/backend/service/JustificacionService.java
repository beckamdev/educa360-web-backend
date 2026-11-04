package com.educa360.backend.service;

import com.educa360.backend.dto.ArchivoDescargaResponse;
import com.educa360.backend.dto.TicketCreateRequest;
import com.educa360.backend.dto.TicketResolverRequest;
import com.educa360.backend.dto.TicketResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.HorarioBloque;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Secretaria;
import com.educa360.backend.entity.TicketJustificacion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.AsistenciaRepository;
import com.educa360.backend.repository.HorarioBloqueRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.TicketJustificacionRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Justificaciones de inasistencia (Módulo 7).
 *
 * Reglas que gobierna:
 * - sólo el apoderado con vínculo activo crea tickets de su representado;
 * - la fecha no puede ser futura;
 * - un ticket es de todo el día (bloque NULL) o de un bloque concreto;
 * - si hay bloque, debe ser de la sección de la matrícula del alumno y la
 *   fecha debe caer en el día de ese bloque;
 * - no se solapan dos tickets activos para el mismo alumno y fecha: el de
 *   día completo prevalece;
 * - sólo Secretaría resuelve y sólo desde PENDIENTE;
 * - al aprobar, las FALTA del alcance pasan a JUSTIFICADA y quedan ligadas
 *   al ticket en la misma transacción;
 * - si la asistencia se registra después, AsistenciaService la crea
 *   directamente como JUSTIFICADA;
 * - no se elimina: los estados son la historia.
 */
@Service
public class JustificacionService {

    private static final List<TicketJustificacion.Estado> ACTIVOS =
            List.of(TicketJustificacion.Estado.PENDIENTE,
                    TicketJustificacion.Estado.APROBADO);
    private static final Duration TTL_SUSTENTO = Duration.ofMinutes(5);

    private final TicketJustificacionRepository ticketRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final MatriculaRepository matriculaRepository;
    private final HorarioBloqueRepository horarioRepository;
    private final SecretariaRepository secretariaRepository;
    private final AnioEscolarRepository anioRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ArchivoValidator archivoValidator;
    private final Clock clock;

    @Autowired
    public JustificacionService(TicketJustificacionRepository ticketRepository,
                                AsistenciaRepository asistenciaRepository,
                                ApoderadoRepository apoderadoRepository,
                                ApoderadoEstudianteRepository vinculoRepository,
                                MatriculaRepository matriculaRepository,
                                HorarioBloqueRepository horarioRepository,
                                SecretariaRepository secretariaRepository,
                                AnioEscolarRepository anioRepository,
                                UserRepository userRepository,
                                StorageService storageService,
                                ArchivoValidator archivoValidator) {
        this(ticketRepository, asistenciaRepository, apoderadoRepository, vinculoRepository,
                matriculaRepository, horarioRepository, secretariaRepository, anioRepository,
                userRepository, storageService, archivoValidator, Clock.systemDefaultZone());
    }

    /** Visible para tests: inyecta un reloj falso. */
    JustificacionService(TicketJustificacionRepository ticketRepository,
                         AsistenciaRepository asistenciaRepository,
                         ApoderadoRepository apoderadoRepository,
                         ApoderadoEstudianteRepository vinculoRepository,
                         MatriculaRepository matriculaRepository,
                         HorarioBloqueRepository horarioRepository,
                         SecretariaRepository secretariaRepository,
                         AnioEscolarRepository anioRepository,
                         UserRepository userRepository,
                         StorageService storageService,
                         ArchivoValidator archivoValidator,
                         Clock clock) {
        this.ticketRepository = ticketRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
        this.matriculaRepository = matriculaRepository;
        this.horarioRepository = horarioRepository;
        this.secretariaRepository = secretariaRepository;
        this.anioRepository = anioRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.archivoValidator = archivoValidator;
        this.clock = clock;
    }

    // ==================== Apoderado ====================

    @Transactional(readOnly = true)
    public List<TicketResponse> listarDelHijo(String email, Long hijoId, Long anioId,
                                              TicketJustificacion.Estado estado) {
        Apoderado apoderado = apoderadoDe(email);
        Estudiante estudiante = hijoVinculado(apoderado, hijoId);

        AnioEscolar anio = null;
        if (anioId != null) {
            anio = anioRepository.findById(anioId)
                    .orElseThrow(() -> new NotFoundException(
                            "El año " + anioId + " no existe"));
        }

        Integer anioCalendario = anio != null ? anio.getAnio() : null;
        return ticketRepository
                .findByEstudianteIdOrderByFechaInasistenciaDescCreatedAtDesc(estudiante.getId())
                .stream()
                .filter(ticket -> estado == null || ticket.getEstado() == estado)
                .filter(ticket -> anioCalendario == null
                        || ticket.getFechaInasistencia().getYear() == anioCalendario)
                .map(TicketResponse::desde)
                .toList();
    }

    @Transactional
    public TicketResponse crear(String email, Long hijoId, TicketCreateRequest datos) {
        Apoderado apoderado = apoderadoDe(email);
        Estudiante estudiante = hijoVinculado(apoderado, hijoId);

        LocalDate fecha = datos.fechaInasistencia();
        if (fecha.isAfter(LocalDate.now(clock))) {
            throw new BadRequestException(
                    "No se puede justificar una fecha futura: " + fecha);
        }

        String motivo = textoObligatorio(datos.motivo(), "El motivo");

        Matricula matricula = matriculaDe(estudiante, fecha);
        if (matricula.estaRetirada()) {
            throw new ConflictException("La matrícula " + matricula.getId()
                    + " está RETIRADA: no se justifican inasistencias");
        }

        HorarioBloque bloque = null;
        if (datos.bloqueId() != null) {
            bloque = horarioRepository.findById(datos.bloqueId())
                    .orElseThrow(() -> new NotFoundException(
                            "El bloque " + datos.bloqueId() + " no existe"));
            if (!bloque.getCurso().getSeccion().getId()
                    .equals(matricula.getSeccion().getId())) {
                // 404 a propósito: no revelar bloques de secciones ajenas.
                throw new NotFoundException("El bloque " + datos.bloqueId() + " no existe");
            }
            validarDia(bloque, fecha);
        }

        validarSinSolape(estudiante.getId(), fecha, bloque);

        TicketJustificacion ticket = new TicketJustificacion(
                apoderado, estudiante, fecha, bloque, motivo, null);
        return TicketResponse.desde(ticketRepository.save(ticket));
    }

    /**
     * Adjunta o reemplaza el sustento de un ticket propio en PENDIENTE.
     * El archivo va al bucket privado {@code sustentos}; en la BD sólo queda
     * su ruta. Reemplazar elimina el objeto anterior.
     */
    @Transactional
    public TicketResponse subirSustento(String email, Long ticketId, MultipartFile archivo) {
        Apoderado apoderado = apoderadoDe(email);
        TicketJustificacion ticket = buscar(ticketId);

        if (!ticket.getApoderado().getId().equals(apoderado.getId())) {
            throw new NotFoundException("El ticket " + ticketId + " no existe");
        }
        if (!ticket.estaPendiente()) {
            throw new ConflictException("El ticket " + ticketId + " ya está "
                    + ticket.getEstado() + ": no se puede adjuntar sustento");
        }

        ArchivoValidator.ArchivoValidado validado =
                archivoValidator.validarSustento(archivo);
        String path = rutaSustento(ticket.getId(), validado.nombreSeguro());
        storageService.upload(StorageService.BUCKET_SUSTENTOS, path,
                validado.contenido(), validado.mimeType());

        String anterior = ticket.getStoragePath();
        ticket.setStoragePath(path);
        try {
            TicketResponse respuesta =
                    TicketResponse.desde(ticketRepository.saveAndFlush(ticket));
            if (anterior != null && !anterior.equals(path)) {
                storageService.delete(StorageService.BUCKET_SUSTENTOS, anterior);
            }
            return respuesta;
        } catch (RuntimeException ex) {
            storageService.delete(StorageService.BUCKET_SUSTENTOS, path);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ArchivoDescargaResponse urlSustentoApoderado(String email, Long ticketId) {
        Apoderado apoderado = apoderadoDe(email);
        TicketJustificacion ticket = buscar(ticketId);
        if (!ticket.getApoderado().getId().equals(apoderado.getId())) {
            throw new NotFoundException("El ticket " + ticketId + " no existe");
        }
        return firmarSustento(ticket);
    }

    @Transactional(readOnly = true)
    public ArchivoDescargaResponse urlSustentoSecretaria(String email, Long ticketId) {
        secretariaDe(email);
        return firmarSustento(buscar(ticketId));
    }

    // ==================== Secretaría ====================

    @Transactional(readOnly = true)
    public List<TicketResponse> bandeja(TicketJustificacion.Estado estado, LocalDate fecha) {
        List<TicketJustificacion> tickets;
        if (estado != null && fecha != null) {
            tickets = ticketRepository
                    .findByEstadoAndFechaInasistenciaOrderByCreatedAtDesc(estado, fecha);
        } else if (estado != null) {
            tickets = ticketRepository.findByEstadoOrderByCreatedAtDesc(estado);
        } else if (fecha != null) {
            tickets = ticketRepository.findByFechaInasistenciaOrderByCreatedAtDesc(fecha);
        } else {
            tickets = ticketRepository.findAllByOrderByCreatedAtDesc();
        }
        return tickets.stream().map(TicketResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse detalle(Long id) {
        return TicketResponse.desde(buscar(id));
    }

    @Transactional
    public TicketResponse resolver(String email, Long id, TicketResolverRequest datos) {
        Secretaria secretaria = secretariaDe(email);
        TicketJustificacion ticket = buscar(id);

        if (!ticket.estaPendiente()) {
            throw new ConflictException("El ticket " + id + " ya está "
                    + ticket.getEstado() + ": no se vuelve a resolver");
        }
        if (datos.estado() != TicketJustificacion.Estado.APROBADO
                && datos.estado() != TicketJustificacion.Estado.RECHAZADO) {
            throw new BadRequestException(
                    "Sólo se puede resolver como APROBADO o RECHAZADO");
        }

        ticket.setEstado(datos.estado());
        ticket.setFechaResolucion(LocalDateTime.now(clock));
        ticket.setResueltoPor(secretaria);
        ticket.setObservacionesAdmin(textoOpcional(datos.observacionesAdmin()));

        if (ticket.estaAprobado()) {
            aplicarJustificacion(ticket);
        }

        return TicketResponse.desde(ticketRepository.save(ticket));
    }

    /**
     * Convierte en JUSTIFICADA las FALTA del alcance del ticket.
     *
     * Sólo toca FALTA: una fila PRESENTE o TARDANZA no se degrada. Si aún no
     * hay asistencias registradas no hay nada que cambiar; cuando el docente
     * las registre, AsistenciaService las creará ya justificadas.
     */
    private void aplicarJustificacion(TicketJustificacion ticket) {
        Matricula matricula = matriculaDe(ticket.getEstudiante(),
                ticket.getFechaInasistencia());

        List<Asistencia> asistencias = ticket.getBloque() == null
                ? asistenciaRepository.findByMatriculaIdAndFecha(
                        matricula.getId(), ticket.getFechaInasistencia())
                : asistenciaRepository.findByMatriculaIdAndFechaAndBloqueId(
                        matricula.getId(), ticket.getFechaInasistencia(),
                        ticket.getBloque().getId());

        List<Asistencia> faltas = asistencias.stream()
                .filter(Asistencia::esFalta)
                .toList();
        for (Asistencia falta : faltas) {
            falta.setEstado(Asistencia.Estado.JUSTIFICADA);
            falta.setTicket(ticket);
        }
        asistenciaRepository.saveAll(faltas);
    }

    // ==================== Helpers ====================

    private ArchivoDescargaResponse firmarSustento(TicketJustificacion ticket) {
        if (!ticket.tieneSustento()) {
            throw new NotFoundException("El ticket " + ticket.getId()
                    + " no tiene archivo de sustento");
        }
        String url = storageService.signedUrl(StorageService.BUCKET_SUSTENTOS,
                ticket.getStoragePath(), TTL_SUSTENTO);
        return new ArchivoDescargaResponse(url, LocalDateTime.now(clock).plus(TTL_SUSTENTO));
    }

    private String rutaSustento(Long ticketId, String nombreSeguro) {
        return ticketId + "/" + UUID.randomUUID() + "-" + nombreSeguro;
    }

    private TicketJustificacion buscar(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "El ticket " + id + " no existe"));
    }

    private Apoderado apoderadoDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return apoderadoRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Apoderado"));
    }

    private Secretaria secretariaDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return secretariaRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Secretaria"));
    }

    private Estudiante hijoVinculado(Apoderado apoderado, Long hijoId) {
        ApoderadoEstudiante vinculo = vinculoRepository
                .findByApoderadoIdAndEstudianteId(apoderado.getId(), hijoId)
                .filter(ApoderadoEstudiante::isActivo)
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante " + hijoId + " no está vinculado a este apoderado"));
        Estudiante estudiante = vinculo.getEstudiante();
        if (!estudiante.estaHabilitado()) {
            throw new NotFoundException("El estudiante no está activo");
        }
        return estudiante;
    }

    private Matricula matriculaDe(Estudiante estudiante, LocalDate fecha) {
        return matriculaRepository
                .findByEstudianteIdAndAnioAnio(estudiante.getId(), fecha.getYear())
                .orElseThrow(() -> new NotFoundException("El estudiante "
                        + estudiante.getId() + " no tiene matrícula en el año "
                        + fecha.getYear()));
    }

    private void validarDia(HorarioBloque bloque, LocalDate fecha) {
        DiaSemana esperado = diaDe(fecha);
        if (esperado == null || esperado != bloque.getDia()) {
            throw new BadRequestException("La fecha " + fecha + " no corresponde al "
                    + bloque.getDia() + " del bloque");
        }
    }

    /**
     * Un ticket de día completo choca con cualquier ticket activo de esa
     * fecha; uno de bloque sólo choca con el mismo bloque o con uno de día
     * completo. Así el alcance más amplio prevalece.
     */
    private void validarSinSolape(Long estudianteId, LocalDate fecha,
                                  HorarioBloque bloque) {
        List<TicketJustificacion> activos = ticketRepository
                .findByEstudianteIdAndFechaInasistenciaAndEstadoIn(
                        estudianteId, fecha, ACTIVOS);
        for (TicketJustificacion existente : activos) {
            boolean solapa = existente.getBloque() == null
                    || bloque == null
                    || existente.getBloque().getId().equals(bloque.getId());
            if (solapa) {
                throw new ConflictException("Ya existe un ticket "
                        + existente.getEstado() + " para el estudiante "
                        + estudianteId + " en la fecha " + fecha
                        + (existente.getBloque() == null
                        ? " (día completo)"
                        : " (bloque " + existente.getBloque().getId() + ")"));
            }
        }
    }

    private String textoObligatorio(String valor, String campo) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new BadRequestException(campo + " es obligatorio");
        }
        String limpio = valor.trim();
        if (limpio.length() > 500) {
            throw new BadRequestException(campo + " no puede superar los 500 caracteres");
        }
        return limpio;
    }

    private String textoOpcional(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.length() > 500) {
            throw new BadRequestException(
                    "El texto no puede superar los 500 caracteres");
        }
        return limpio;
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
}
