package com.educa360.backend.service;

import com.educa360.backend.dto.ContadorNotificacionesResponse;
import com.educa360.backend.dto.NotificacionResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.Comunicado;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Notificacion;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.TicketJustificacion;
import com.educa360.backend.entity.TipoNotificacion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.NotificacionRepository;
import com.educa360.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock private NotificacionRepository notificacionRepository;
    @Mock private UserRepository userRepository;
    @Mock private MatriculaRepository matriculaRepository;
    @Mock private ApoderadoEstudianteRepository vinculoRepository;
    @Mock private CursoRepository cursoRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private NotificacionService servicio;

    private User autor;
    private User userAlumno;
    private User userMama;
    private User userDocente;
    private Estudiante alumno;
    private Apoderado mama;
    private ApoderadoEstudiante vinculo;
    private AnioEscolar anio;
    private Seccion seccion;
    private Matricula matricula;
    private Docente docente;
    private Curso curso;

    @BeforeEach
    void prepararEscenario() {
        autor = user(1L, "admin@educa360.pe", User.Role.ADMIN);
        userAlumno = user(4L, "alma@educa360.pe", User.Role.ESTUDIANTE);
        userMama = user(5L, "lucia@educa360.pe", User.Role.APODERADO);
        userDocente = user(3L, "dana@educa360.pe", User.Role.DOCENTE);

        anio = new AnioEscolar(2026, "Año Escolar 2026", LocalDate.of(2026, 3, 2));
        anio.setId(10L);
        anio.setEstado(AnioEscolar.Estado.ABIERTO);

        seccion = new Seccion(3, "A", "A-101", null, anio);
        seccion.setId(30L);

        alumno = new Estudiante(userAlumno, LocalDate.of(2011, 3, 3));
        alumno.setId(60L);

        matricula = new Matricula(alumno, anio, seccion, LocalDate.of(2026, 2, 20));
        matricula.setId(70L);

        mama = new Apoderado(userMama);
        mama.setId(80L);
        vinculo = new ApoderadoEstudiante(
                mama, alumno, ApoderadoEstudiante.Parentesco.MADRE, true);

        docente = new Docente(userDocente, "Licenciada");
        docente.setId(20L);
        Materia materia = new Materia("Matemática", null);
        materia.setId(40L);
        curso = new Curso(docente, materia, seccion);
        curso.setId(50L);

        lenient().when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== Bandeja REST ====================

    @Test
    @DisplayName("Lista solo las notificaciones del usuario autenticado")
    void listaPorUsuario() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userAlumno));
        Notificacion notificacion = notificacion(100L, userAlumno, false);
        when(notificacionRepository.findByUsuarioIdOrderByCreatedAtDesc(4L))
                .thenReturn(List.of(notificacion));

        List<NotificacionResponse> lista = servicio.listar("alma@educa360.pe", null);

        assertEquals(1, lista.size());
        assertEquals(100L, lista.getFirst().id());
        verify(notificacionRepository).findByUsuarioIdOrderByCreatedAtDesc(4L);
    }

    @Test
    @DisplayName("Filtra por leída/no leída")
    void listaFiltrada() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userAlumno));
        when(notificacionRepository.findByUsuarioIdAndLeidaOrderByCreatedAtDesc(4L, false))
                .thenReturn(List.of());

        assertTrue(servicio.listar("alma@educa360.pe", false).isEmpty());
        verify(notificacionRepository)
                .findByUsuarioIdAndLeidaOrderByCreatedAtDesc(4L, false);
    }

    @Test
    @DisplayName("El contador solo cuenta las no leídas")
    void contador() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userAlumno));
        when(notificacionRepository.countByUsuarioIdAndLeidaFalse(4L)).thenReturn(3L);

        ContadorNotificacionesResponse respuesta = servicio.contador("alma@educa360.pe");

        assertEquals(3L, respuesta.noLeidas());
    }

    @Test
    @DisplayName("Marcar como leída fija leidaAt y es idempotente")
    void marcarLeida() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userAlumno));
        Notificacion notificacion = notificacion(100L, userAlumno, false);
        when(notificacionRepository.findByIdAndUsuarioId(100L, 4L))
                .thenReturn(Optional.of(notificacion));

        NotificacionResponse respuesta = servicio.marcarLeida("alma@educa360.pe", 100L);

        assertTrue(respuesta.leida());
        assertNotNull(respuesta.leidaAt());
        LocalDateTime primeraLectura = notificacion.getLeidaAt();

        NotificacionResponse segunda = servicio.marcarLeida("alma@educa360.pe", 100L);
        assertTrue(segunda.leida());
        assertEquals(primeraLectura, notificacion.getLeidaAt());
    }

    @Test
    @DisplayName("No se puede marcar una notificación ajena")
    void marcarAjena() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userAlumno));
        when(notificacionRepository.findByIdAndUsuarioId(99L, 4L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> servicio.marcarLeida("alma@educa360.pe", 99L));
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Marcar todas las leídas deja el contador en cero")
    void marcarTodas() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userAlumno));
        when(notificacionRepository.marcarTodasLeidas(eq(4L), any(LocalDateTime.class)))
                .thenReturn(2);

        ContadorNotificacionesResponse respuesta =
                servicio.marcarTodasLeidas("alma@educa360.pe");

        assertEquals(0L, respuesta.noLeidas());
        verify(notificacionRepository).marcarTodasLeidas(eq(4L), any(LocalDateTime.class));
    }

    // ==================== Destinatarios ====================

    @Test
    @DisplayName("Un comunicado general avisa a alumnos, apoderados y docentes")
    void comunicadoGeneral() {
        Comunicado comunicado = new Comunicado(autor, anio, null, "Bienvenidos");
        comunicado.setId(200L);
        when(matriculaRepository.findByAnioId(10L)).thenReturn(List.of(matricula));
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(60L))
                .thenReturn(List.of(vinculo));
        when(cursoRepository.findBySeccionAnioId(10L)).thenReturn(List.of(curso));

        servicio.notificarComunicado(comunicado);

        ArgumentCaptor<Notificacion> captor =
                ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, times(3)).save(captor.capture());
        Set<Long> destinatarios = captor.getAllValues().stream()
                .map(notificacion -> notificacion.getUsuario().getId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(3L, 4L, 5L), destinatarios);
        assertTrue(captor.getAllValues().stream()
                .allMatch(n -> n.getTipo() == TipoNotificacion.COMUNICADO));
        verify(messagingTemplate, times(3)).convertAndSendToUser(
                anyString(), eq(NotificacionService.DESTINO),
                any(NotificacionResponse.class));
    }

    @Test
    @DisplayName("Deduplica destinatarios por usuario")
    void deduplicaDestinatarios() {
        Comunicado comunicado = new Comunicado(autor, anio, null, "Bienvenidos");
        comunicado.setId(200L);
        Apoderado otroApoderado = new Apoderado(userMama);
        otroApoderado.setId(81L);
        ApoderadoEstudiante vinculoDuplicado = new ApoderadoEstudiante(
                otroApoderado, alumno, ApoderadoEstudiante.Parentesco.MADRE, false);

        when(matriculaRepository.findByAnioId(10L)).thenReturn(List.of(matricula));
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(60L))
                .thenReturn(List.of(vinculo, vinculoDuplicado));
        when(cursoRepository.findBySeccionAnioId(10L)).thenReturn(List.of(curso));

        servicio.notificarComunicado(comunicado);

        // alumno + apoderado (una sola vez) + docente
        verify(notificacionRepository, times(3)).save(any(Notificacion.class));
    }

    @Test
    @DisplayName("El ticket resuelto avisa al alumno y a sus apoderados")
    void ticketResuelto() {
        TicketJustificacion ticket = new TicketJustificacion(
                mama, alumno, LocalDate.of(2026, 3, 9), null, "Cita médica", null);
        ticket.setId(300L);
        ticket.setEstado(TicketJustificacion.Estado.APROBADO);
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(60L))
                .thenReturn(List.of(vinculo));

        servicio.notificarTicketResuelto(ticket);

        ArgumentCaptor<Notificacion> captor =
                ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(n -> n.getTipo() == TipoNotificacion.TICKET_RESUELTO));
        assertTrue(captor.getAllValues().getFirst().getMensaje().contains("aprobado"));
    }

    @Test
    @DisplayName("La calificación formatea la nota con dos decimales")
    void calificacion() {
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(60L))
                .thenReturn(List.of(vinculo));

        servicio.notificarCalificacion(matricula, "Matemática", "Examen parcial",
                new BigDecimal("15.5"), 400L);

        ArgumentCaptor<Notificacion> captor =
                ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().getFirst().getMensaje().contains("15.50"));
    }

    @Test
    @DisplayName("La falta registrada avisa al alumno y a sus apoderados")
    void falta() {
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(60L))
                .thenReturn(List.of(vinculo));

        servicio.notificarAsistencia(matricula, Asistencia.Estado.FALTA,
                LocalDate.of(2026, 3, 9), "Matemática", 500L);

        ArgumentCaptor<Notificacion> captor =
                ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, times(2)).save(captor.capture());
        assertEquals("Falta registrada", captor.getAllValues().getFirst().getTitulo());
    }

    @Test
    @DisplayName("El material publicado avisa a los alumnos de la sección")
    void material() {
        when(matriculaRepository.findBySeccionId(30L)).thenReturn(List.of(matricula));

        servicio.notificarMaterial(curso, "Guía de ejercicios", 600L);

        ArgumentCaptor<Notificacion> captor =
                ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, times(1)).save(captor.capture());
        assertEquals(TipoNotificacion.MATERIAL, captor.getValue().getTipo());
        assertTrue(captor.getValue().getTitulo().contains("Matemática"));
    }

    @Test
    @DisplayName("El cierre avisa a los alumnos y apoderados del año")
    void cierre() {
        when(matriculaRepository.findByAnioId(10L)).thenReturn(List.of(matricula));
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(60L))
                .thenReturn(List.of(vinculo));

        servicio.notificarCierre(anio);

        ArgumentCaptor<Notificacion> captor =
                ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(n -> n.getTipo() == TipoNotificacion.CIERRE));
    }

    @Test
    @DisplayName("El mensaje largo se recorta a 500 caracteres")
    void recortaMensaje() {
        Comunicado comunicado = new Comunicado(autor, anio, null, "x".repeat(700));
        comunicado.setId(200L);
        when(matriculaRepository.findByAnioId(10L)).thenReturn(List.of(matricula));
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(60L))
                .thenReturn(List.of(vinculo));
        when(cursoRepository.findBySeccionAnioId(10L)).thenReturn(List.of(curso));

        servicio.notificarComunicado(comunicado);

        ArgumentCaptor<Notificacion> captor =
                ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, times(3)).save(captor.capture());
        assertTrue(captor.getAllValues().getFirst().getMensaje().length() <= 500);
        assertTrue(captor.getAllValues().getFirst().getMensaje().endsWith("…"));
    }

    @Test
    @DisplayName("Un usuario inactivo no recibe notificaciones")
    void usuarioInactivoNoRecibe() {
        userMama.setActivo(false);
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(60L))
                .thenReturn(List.of(vinculo));

        servicio.notificarCalificacion(matricula, "Matemática", "Examen",
                new BigDecimal("10.00"), 400L);

        // Solo el alumno; el apoderado está inactivo.
        verify(notificacionRepository, times(1)).save(any(Notificacion.class));
    }

    // ==================== Helpers ====================

    private User user(Long id, String email, User.Role rol) {
        User user = new User(email, "password", "Nombre", "Apellido",
                "dni-" + id, rol);
        user.setId(id);
        return user;
    }

    private Notificacion notificacion(Long id, User usuario, boolean leida) {
        Notificacion notificacion = new Notificacion(usuario,
                TipoNotificacion.COMUNICADO, "Título", "Mensaje", 1L);
        notificacion.setId(id);
        notificacion.setLeida(leida);
        return notificacion;
    }
}
