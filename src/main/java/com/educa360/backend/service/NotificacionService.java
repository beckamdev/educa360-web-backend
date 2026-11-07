package com.educa360.backend.service;

import com.educa360.backend.dto.ContadorNotificacionesResponse;
import com.educa360.backend.dto.NotificacionResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.Comunicado;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Notificacion;
import com.educa360.backend.entity.TicketJustificacion;
import com.educa360.backend.entity.TipoNotificacion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.NotificacionRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistencia y entrega de notificaciones.
 *
 * La BD es la fuente de verdad: cada evento guarda una fila por destinatario.
 * El WebSocket solo acelera la entrega y se dispara después del commit, para
 * no avisar de algo que la transacción podría revertir.
 *
 * La resolución de destinatarios vive aquí para que los servicios de negocio
 * solo expresen "ocurrió este evento" y no repitan consultas de audiencia.
 */
@Service
public class NotificacionService {

    /** Destino al que el cliente se suscribe con STOMP. */
    public static final String DESTINO = "/queue/notificaciones";

    private static final int MAX_TITULO = 120;
    private static final int MAX_MENSAJE = 500;

    private final NotificacionRepository notificacionRepository;
    private final UserRepository userRepository;
    private final MatriculaRepository matriculaRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final CursoRepository cursoRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificacionService(NotificacionRepository notificacionRepository,
                               UserRepository userRepository,
                               MatriculaRepository matriculaRepository,
                               ApoderadoEstudianteRepository vinculoRepository,
                               CursoRepository cursoRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificacionRepository = notificacionRepository;
        this.userRepository = userRepository;
        this.matriculaRepository = matriculaRepository;
        this.vinculoRepository = vinculoRepository;
        this.cursoRepository = cursoRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // ==================== API del usuario ====================

    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(String email, Boolean leida) {
        User usuario = usuarioDe(email);
        List<Notificacion> lista = leida == null
                ? notificacionRepository.findByUsuarioIdOrderByCreatedAtDesc(usuario.getId())
                : notificacionRepository.findByUsuarioIdAndLeidaOrderByCreatedAtDesc(
                        usuario.getId(), leida);
        return lista.stream().map(NotificacionResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public ContadorNotificacionesResponse contador(String email) {
        User usuario = usuarioDe(email);
        return new ContadorNotificacionesResponse(
                notificacionRepository.countByUsuarioIdAndLeidaFalse(usuario.getId()));
    }

    @Transactional
    public NotificacionResponse marcarLeida(String email, Long id) {
        User usuario = usuarioDe(email);
        Notificacion notificacion = notificacionRepository
                .findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new NotFoundException(
                        "La notificación " + id + " no existe"));
        notificacion.marcarLeida();
        return NotificacionResponse.desde(notificacionRepository.save(notificacion));
    }

    @Transactional
    public ContadorNotificacionesResponse marcarTodasLeidas(String email) {
        User usuario = usuarioDe(email);
        notificacionRepository.marcarTodasLeidas(usuario.getId(), LocalDateTime.now());
        return new ContadorNotificacionesResponse(0);
    }

    // ==================== Eventos de negocio ====================

    @Transactional
    public void notificarComunicado(Comunicado comunicado) {
        Map<Long, User> destinatarios = destinatariosDeComunicado(comunicado);
        destinatarios.remove(comunicado.getAutor().getId());

        String titulo = comunicado.esGeneral()
                ? "Nuevo comunicado general"
                : "Nuevo comunicado · " + comunicado.getSeccion().getEtiqueta();
        crearParaUsuarios(destinatarios.values(), TipoNotificacion.COMUNICADO,
                titulo, recortar(comunicado.getContenido(), MAX_MENSAJE),
                comunicado.getId());
    }

    @Transactional
    public void notificarTicketResuelto(TicketJustificacion ticket) {
        Map<Long, User> destinatarios = destinatariosDeEstudiante(
                ticket.getEstudiante(), false);

        String estado = ticket.estaAprobado() ? "aprobado" : "rechazado";
        String titulo = "Ticket " + estado;
        String mensaje = "La justificación del " + ticket.getFechaInasistencia()
                + " fue " + estado + ".";
        crearParaUsuarios(destinatarios.values(), TipoNotificacion.TICKET_RESUELTO,
                titulo, mensaje, ticket.getId());
    }

    @Transactional
    public void notificarCalificacion(Matricula matricula, String materia,
                                      String criterio, BigDecimal nota,
                                      Long calificacionId) {
        Map<Long, User> destinatarios = destinatariosDeEstudiante(
                matricula.getEstudiante(), true);

        String titulo = "Nueva calificación";
        String mensaje = materia + " · " + criterio + ": "
                + nota.setScale(2, RoundingMode.HALF_UP).toPlainString();
        crearParaUsuarios(destinatarios.values(), TipoNotificacion.CALIFICACION,
                titulo, mensaje, calificacionId);
    }

    @Transactional
    public void notificarCalificacionEliminada(Matricula matricula, String materia,
                                               String criterio, Long calificacionId) {
        Map<Long, User> destinatarios = destinatariosDeEstudiante(
                matricula.getEstudiante(), true);

        String titulo = "Calificación eliminada";
        String mensaje = materia + " · " + criterio + " ya no está disponible.";
        crearParaUsuarios(destinatarios.values(), TipoNotificacion.CALIFICACION,
                titulo, mensaje, calificacionId);
    }

    @Transactional
    public void notificarAsistencia(Matricula matricula, Asistencia.Estado estado,
                                    LocalDate fecha, String materia, Long asistenciaId) {
        Map<Long, User> destinatarios = destinatariosDeEstudiante(
                matricula.getEstudiante(), true);

        String titulo = estado == Asistencia.Estado.FALTA
                ? "Falta registrada"
                : "Tardanza registrada";
        String mensaje = (estado == Asistencia.Estado.FALTA ? "Se registró una falta" : "Se registró una tardanza")
                + " el " + fecha + " en " + materia + ".";
        crearParaUsuarios(destinatarios.values(), TipoNotificacion.ASISTENCIA,
                titulo, mensaje, asistenciaId);
    }

    @Transactional
    public void notificarMaterial(Curso curso, String tituloMaterial, Long materialId) {
        Map<Long, User> destinatarios = new LinkedHashMap<>();
        for (Matricula matricula : matriculaRepository
                .findBySeccionId(curso.getSeccion().getId())) {
            if (matricula.estaRetirada()) {
                continue;
            }
            agregarUsuario(destinatarios, matricula.getEstudiante().getUser());
        }

        String titulo = "Nuevo material en " + curso.getMateria().getNombre();
        crearParaUsuarios(destinatarios.values(), TipoNotificacion.MATERIAL,
                titulo, recortar(tituloMaterial, MAX_MENSAJE), materialId);
    }

    @Transactional
    public void notificarCierre(AnioEscolar anio) {
        Map<Long, User> destinatarios = new LinkedHashMap<>();
        for (Matricula matricula : matriculaRepository.findByAnioId(anio.getId())) {
            if (matricula.estaRetirada()) {
                continue;
            }
            Estudiante estudiante = matricula.getEstudiante();
            if (estudiante.estaHabilitado()) {
                agregarUsuario(destinatarios, estudiante.getUser());
                agregarApoderados(destinatarios, estudiante.getId());
            }
        }

        crearParaUsuarios(destinatarios.values(), TipoNotificacion.CIERRE,
                "Año escolar cerrado",
                "El " + anio.getNombre() + " ha sido cerrado.",
                anio.getId());
    }

    // ==================== Destinatarios ====================

    private Map<Long, User> destinatariosDeComunicado(Comunicado comunicado) {
        Map<Long, User> destinatarios = new LinkedHashMap<>();
        boolean general = comunicado.esGeneral();
        Long anioId = comunicado.getAnio().getId();
        Long seccionId = general ? null : comunicado.getSeccion().getId();

        List<Matricula> matriculas = general
                ? matriculaRepository.findByAnioId(anioId)
                : matriculaRepository.findBySeccionId(seccionId);
        for (Matricula matricula : matriculas) {
            if (matricula.estaRetirada()) {
                continue;
            }
            Estudiante estudiante = matricula.getEstudiante();
            if (!estudiante.estaHabilitado()) {
                continue;
            }
            agregarUsuario(destinatarios, estudiante.getUser());
            agregarApoderados(destinatarios, estudiante.getId());
        }

        List<Curso> cursos = general
                ? cursoRepository.findBySeccionAnioId(anioId)
                : cursoRepository.findBySeccionId(seccionId);
        for (Curso curso : cursos) {
            agregarUsuario(destinatarios, curso.getDocente().getUser());
        }
        if (!general && comunicado.getSeccion().getTutor() != null) {
            agregarUsuario(destinatarios,
                    comunicado.getSeccion().getTutor().getUser());
        }
        return destinatarios;
    }

    private Map<Long, User> destinatariosDeEstudiante(Estudiante estudiante,
                                                      boolean soloHabilitado) {
        Map<Long, User> destinatarios = new LinkedHashMap<>();
        if (!soloHabilitado || estudiante.estaHabilitado()) {
            agregarUsuario(destinatarios, estudiante.getUser());
        }
        agregarApoderados(destinatarios, estudiante.getId());
        return destinatarios;
    }

    private void agregarApoderados(Map<Long, User> destinatarios, Long estudianteId) {
        for (ApoderadoEstudiante vinculo
                : vinculoRepository.findByEstudianteIdAndActivoTrue(estudianteId)) {
            agregarUsuario(destinatarios, vinculo.getApoderado().getUser());
        }
    }

    private void agregarUsuario(Map<Long, User> destinatarios, User usuario) {
        if (usuario != null && usuario.isActivo()) {
            destinatarios.putIfAbsent(usuario.getId(), usuario);
        }
    }

    // ==================== Persistencia y push ====================

    private void crearParaUsuarios(Iterable<User> destinatarios, TipoNotificacion tipo,
                                   String titulo, String mensaje, Long referenciaId) {
        List<Notificacion> creadas = new ArrayList<>();
        for (User usuario : destinatarios) {
            Notificacion notificacion = new Notificacion(usuario, tipo,
                    recortar(titulo, MAX_TITULO), recortar(mensaje, MAX_MENSAJE),
                    referenciaId);
            creadas.add(notificacionRepository.save(notificacion));
        }
        if (!creadas.isEmpty()) {
            programarEnvio(creadas);
        }
    }

    /**
     * El push se emite después del commit. Si no hay transacción (tests
     * unitarios), se envía de inmediato.
     */
    private void programarEnvio(List<Notificacion> creadas) {
        List<NotificacionResponse> payloads = creadas.stream()
                .map(NotificacionResponse::desde)
                .toList();
        List<String> emails = creadas.stream()
                .map(notificacion -> notificacion.getUsuario().getEmail())
                .toList();

        Runnable envio = () -> {
            for (int i = 0; i < payloads.size(); i++) {
                messagingTemplate.convertAndSendToUser(
                        emails.get(i), DESTINO, payloads.get(i));
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            envio.run();
                        }
                    });
        } else {
            envio.run();
        }
    }

    // ==================== Helpers ====================

    private User usuarioDe(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.trim().replaceAll("\\s+", " ");
        if (limpio.length() <= maximo) {
            return limpio;
        }
        return limpio.substring(0, maximo - 1) + "…";
    }
}
