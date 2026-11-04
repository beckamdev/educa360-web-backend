package com.educa360.backend.service;

import com.educa360.backend.dto.TicketCreateRequest;
import com.educa360.backend.dto.TicketResolverRequest;
import com.educa360.backend.dto.TicketResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.HorarioBloque;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link JustificacionService} sin Spring ni base de datos.
 *
 * Cubre vínculo del apoderado, fecha futura, bloque de la sección y del día,
 * solapes de alcance, resolución única y el paso de FALTA a JUSTIFICADA.
 */
@ExtendWith(MockitoExtension.class)
class JustificacionServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 9); // lunes
    private static final LocalDate FUTURA = LocalDate.of(2026, 3, 11);
    private static final LocalDate HOY = LocalDate.of(2026, 3, 10);
    private static final ZoneId ZONA = ZoneId.of("UTC");

    @Mock
    private TicketJustificacionRepository ticketRepository;

    @Mock
    private AsistenciaRepository asistenciaRepository;

    @Mock
    private ApoderadoRepository apoderadoRepository;

    @Mock
    private ApoderadoEstudianteRepository vinculoRepository;

    @Mock
    private MatriculaRepository matriculaRepository;

    @Mock
    private HorarioBloqueRepository horarioRepository;

    @Mock
    private SecretariaRepository secretariaRepository;

    @Mock
    private AnioEscolarRepository anioRepository;

    @Mock
    private UserRepository userRepository;

    private User userApoderado;
    private Apoderado apoderado;
    private User userAlumno;
    private Estudiante estudiante;
    private Matricula matricula;
    private AnioEscolar anio;
    private Seccion seccion;
    private HorarioBloque bloque;
    private User userSecretaria;
    private Secretaria secretaria;

    private JustificacionService servicio;

    @BeforeEach
    void setUp() {
        userApoderado = user("lucia@educa360.pe", User.Role.APODERADO, "Lucia", "40000001");
        apoderado = new Apoderado(userApoderado);
        apoderado.setId(1L);
        userAlumno = user("alma@educa360.pe", User.Role.ESTUDIANTE, "Alma", "30000001");
        estudiante = new Estudiante(userAlumno, LocalDate.of(2011, 3, 3));
        estudiante.setId(60L);
        anio = new AnioEscolar(2026, "Año Escolar 2026", LocalDate.of(2026, 3, 2));
        anio.setId(10L);
        seccion = new Seccion(3, "A", "A-101", null, anio);
        seccion.setId(30L);
        matricula = new Matricula(estudiante, anio, seccion, LocalDate.of(2026, 2, 20));
        matricula.setId(70L);
        bloque = bloque(50L, seccion, DiaSemana.LUNES, LocalTime.of(8, 0));
        userSecretaria = user("sofia@educa360.pe", User.Role.SECRETARIA, "Sofia", "20000001");
        secretaria = new Secretaria(userSecretaria);
        secretaria.setId(2L);

        servicio = new JustificacionService(ticketRepository, asistenciaRepository,
                apoderadoRepository, vinculoRepository, matriculaRepository, horarioRepository,
                secretariaRepository, anioRepository, userRepository,
                Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"), ZONA));
    }

    @Test
    @DisplayName("El apoderado crea un ticket de día completo en PENDIENTE")
    void creaTicketDeDiaCompleto() {
        prepararApoderadoYMatricula();
        when(ticketRepository.findByEstudianteIdAndFechaInasistenciaAndEstadoIn(
                60L, FECHA, List.of(TicketJustificacion.Estado.PENDIENTE,
                        TicketJustificacion.Estado.APROBADO)))
                .thenReturn(List.of());
        when(ticketRepository.save(any(TicketJustificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TicketResponse respuesta = servicio.crear("lucia@educa360.pe", 60L,
                new TicketCreateRequest(FECHA, null, "  Cita médica  ", "  "));

        assertEquals(TicketJustificacion.Estado.PENDIENTE, respuesta.estado());
        assertEquals("Cita médica", respuesta.motivo());
        assertNull(respuesta.urlArchivoSustento());
        assertNull(respuesta.bloqueId());
        assertEquals(apoderado.getId(), respuesta.apoderadoId());
        assertEquals(estudiante.getId(), respuesta.estudianteId());
    }

    @Test
    @DisplayName("No se justifica una fecha futura")
    void rechazaFechaFutura() {
        prepararApoderadoYMatricula();

        assertThrows(BadRequestException.class, () -> servicio.crear(
                "lucia@educa360.pe", 60L,
                new TicketCreateRequest(FUTURA, null, "Cita médica", null)));
    }

    @Test
    @DisplayName("Un apoderado no crea tickets para un hijo no vinculado")
    void rechazaHijoAjeno() {
        when(userRepository.findByEmail("lucia@educa360.pe"))
                .thenReturn(Optional.of(userApoderado));
        when(apoderadoRepository.findByUser(userApoderado)).thenReturn(Optional.of(apoderado));
        when(vinculoRepository.findByApoderadoIdAndEstudianteId(1L, 60L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> servicio.crear(
                "lucia@educa360.pe", 60L,
                new TicketCreateRequest(FECHA, null, "Cita médica", null)));
    }

    @Test
    @DisplayName("El bloque debe ser de la sección del alumno")
    void rechazaBloqueDeOtraSeccion() {
        prepararApoderadoYMatricula();
        Seccion otra = new Seccion(3, "B", "B-101", null, anio);
        otra.setId(31L);
        HorarioBloque ajeno = bloque(51L, otra, DiaSemana.LUNES, LocalTime.of(8, 0));
        when(horarioRepository.findById(51L)).thenReturn(Optional.of(ajeno));

        assertThrows(NotFoundException.class, () -> servicio.crear(
                "lucia@educa360.pe", 60L,
                new TicketCreateRequest(FECHA, 51L, "Cita médica", null)));
    }

    @Test
    @DisplayName("La fecha del ticket debe caer en el día del bloque")
    void rechazaDiaQueNoCorresponde() {
        prepararApoderadoYMatricula();
        when(horarioRepository.findById(50L)).thenReturn(Optional.of(bloque));

        assertThrows(BadRequestException.class, () -> servicio.crear(
                "lucia@educa360.pe", 60L,
                new TicketCreateRequest(FECHA.plusDays(1), 50L, "Cita médica", null)));
    }

    @Test
    @DisplayName("Un ticket de día completo bloquea cualquier ticket de esa fecha")
    void rechazaSolapeConDiaCompleto() {
        prepararApoderadoYMatricula();
        when(horarioRepository.findById(50L)).thenReturn(Optional.of(bloque));
        TicketJustificacion existente = ticket(90L, null,
                TicketJustificacion.Estado.PENDIENTE);
        when(ticketRepository.findByEstudianteIdAndFechaInasistenciaAndEstadoIn(
                60L, FECHA, List.of(TicketJustificacion.Estado.PENDIENTE,
                        TicketJustificacion.Estado.APROBADO)))
                .thenReturn(List.of(existente));

        assertThrows(ConflictException.class, () -> servicio.crear(
                "lucia@educa360.pe", 60L,
                new TicketCreateRequest(FECHA, 50L, "Cita médica", null)));
    }

    @Test
    @DisplayName("Dos tickets de bloques distintos el mismo día sí conviven")
    void permiteBloquesDistintos() {
        prepararApoderadoYMatricula();
        HorarioBloque otroBloque = bloque(51L, seccion, DiaSemana.LUNES, LocalTime.of(10, 0));
        when(horarioRepository.findById(51L)).thenReturn(Optional.of(otroBloque));
        TicketJustificacion existente = ticket(90L, bloque,
                TicketJustificacion.Estado.PENDIENTE);
        when(ticketRepository.findByEstudianteIdAndFechaInasistenciaAndEstadoIn(
                60L, FECHA, List.of(TicketJustificacion.Estado.PENDIENTE,
                        TicketJustificacion.Estado.APROBADO)))
                .thenReturn(List.of(existente));
        when(ticketRepository.save(any(TicketJustificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TicketResponse respuesta = servicio.crear("lucia@educa360.pe", 60L,
                new TicketCreateRequest(FECHA, 51L, "Cita médica", null));

        assertEquals(51L, respuesta.bloqueId());
        assertEquals(TicketJustificacion.Estado.PENDIENTE, respuesta.estado());
    }

    @Test
    @DisplayName("Aprobar convierte las FALTA en JUSTIFICADA y no toca PRESENTE")
    void aprobarJustificaFaltas() {
        prepararSecretaria();
        TicketJustificacion pendiente = ticket(90L, null,
                TicketJustificacion.Estado.PENDIENTE);
        when(ticketRepository.findById(90L)).thenReturn(Optional.of(pendiente));
        when(matriculaRepository.findByEstudianteIdAndAnioAnio(60L, 2026))
                .thenReturn(Optional.of(matricula));
        Asistencia falta = asistencia(80L, Asistencia.Estado.FALTA);
        Asistencia presente = asistencia(81L, Asistencia.Estado.PRESENTE);
        when(asistenciaRepository.findByMatriculaIdAndFecha(70L, FECHA))
                .thenReturn(List.of(falta, presente));
        when(ticketRepository.save(any(TicketJustificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TicketResponse respuesta = servicio.resolver("sofia@educa360.pe", 90L,
                new TicketResolverRequest(TicketJustificacion.Estado.APROBADO,
                        "  Documento válido  "));

        assertEquals(TicketJustificacion.Estado.APROBADO, respuesta.estado());
        assertNotNull(respuesta.fechaResolucion());
        assertEquals(secretaria.getId(), respuesta.resueltoPorId());
        assertEquals("Documento válido", respuesta.observacionesAdmin());
        assertEquals(Asistencia.Estado.JUSTIFICADA, falta.getEstado());
        assertEquals(pendiente, falta.getTicket());
        assertEquals(Asistencia.Estado.PRESENTE, presente.getEstado());
        assertNull(presente.getTicket());
        verify(asistenciaRepository).saveAll(List.of(falta));
    }

    @Test
    @DisplayName("Rechazar no toca la asistencia")
    void rechazarNoTocaAsistencia() {
        prepararSecretaria();
        TicketJustificacion pendiente = ticket(90L, null,
                TicketJustificacion.Estado.PENDIENTE);
        when(ticketRepository.findById(90L)).thenReturn(Optional.of(pendiente));
        when(ticketRepository.save(any(TicketJustificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TicketResponse respuesta = servicio.resolver("sofia@educa360.pe", 90L,
                new TicketResolverRequest(TicketJustificacion.Estado.RECHAZADO, null));

        assertEquals(TicketJustificacion.Estado.RECHAZADO, respuesta.estado());
        verify(asistenciaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Un ticket ya resuelto no se vuelve a resolver")
    void rechazaResolverDosVeces() {
        prepararSecretaria();
        TicketJustificacion aprobado = ticket(90L, null,
                TicketJustificacion.Estado.APROBADO);
        when(ticketRepository.findById(90L)).thenReturn(Optional.of(aprobado));

        assertThrows(ConflictException.class, () -> servicio.resolver(
                "sofia@educa360.pe", 90L,
                new TicketResolverRequest(TicketJustificacion.Estado.RECHAZADO, null)));
    }

    @Test
    @DisplayName("No se resuelve un ticket como PENDIENTE")
    void rechazaResolverComoPendiente() {
        prepararSecretaria();
        TicketJustificacion pendiente = ticket(90L, null,
                TicketJustificacion.Estado.PENDIENTE);
        when(ticketRepository.findById(90L)).thenReturn(Optional.of(pendiente));

        assertThrows(BadRequestException.class, () -> servicio.resolver(
                "sofia@educa360.pe", 90L,
                new TicketResolverRequest(TicketJustificacion.Estado.PENDIENTE, null)));
    }

    @Test
    @DisplayName("El listado del apoderado filtra por año y estado")
    void listarFiltraPorAnioYEstado() {
        prepararApoderadoYMatricula();
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        TicketJustificacion pendiente = ticket(90L, null,
                TicketJustificacion.Estado.PENDIENTE);
        TicketJustificacion aprobado = ticket(91L, null,
                TicketJustificacion.Estado.APROBADO);
        when(ticketRepository.findByEstudianteIdOrderByFechaInasistenciaDescCreatedAtDesc(60L))
                .thenReturn(List.of(pendiente, aprobado));

        List<TicketResponse> respuesta = servicio.listarDelHijo(
                "lucia@educa360.pe", 60L, 10L, TicketJustificacion.Estado.PENDIENTE);

        assertEquals(1, respuesta.size());
        assertEquals(TicketJustificacion.Estado.PENDIENTE, respuesta.get(0).estado());
    }

    // ==================== Helpers ====================

    private void prepararApoderadoYMatricula() {
        lenient().when(userRepository.findByEmail("lucia@educa360.pe"))
                .thenReturn(Optional.of(userApoderado));
        lenient().when(apoderadoRepository.findByUser(userApoderado))
                .thenReturn(Optional.of(apoderado));
        ApoderadoEstudiante vinculo = new ApoderadoEstudiante(
                apoderado, estudiante, ApoderadoEstudiante.Parentesco.MADRE, true);
        lenient().when(vinculoRepository.findByApoderadoIdAndEstudianteId(1L, 60L))
                .thenReturn(Optional.of(vinculo));
        lenient().when(matriculaRepository.findByEstudianteIdAndAnioAnio(60L, 2026))
                .thenReturn(Optional.of(matricula));
    }

    private void prepararSecretaria() {
        lenient().when(userRepository.findByEmail("sofia@educa360.pe"))
                .thenReturn(Optional.of(userSecretaria));
        lenient().when(secretariaRepository.findByUser(userSecretaria))
                .thenReturn(Optional.of(secretaria));
    }

    private User user(String email, User.Role rol, String nombres, String dni) {
        User user = new User();
        user.setEmail(email);
        user.setNombres(nombres);
        user.setApellidos("Luna");
        user.setDni(dni);
        user.setRol(rol);
        user.setActivo(true);
        return user;
    }

    private HorarioBloque bloque(Long id, Seccion seccion, DiaSemana dia, LocalTime inicio) {
        Materia materia = new Materia("Matemática", null);
        materia.setId(5L);
        Curso curso = new Curso(null, materia, seccion);
        curso.setId(40L);
        HorarioBloque entidad = new HorarioBloque(curso, dia, inicio, inicio.plusHours(1));
        entidad.setId(id);
        return entidad;
    }

    private TicketJustificacion ticket(Long id, HorarioBloque bloque,
                                       TicketJustificacion.Estado estado) {
        TicketJustificacion entidad = new TicketJustificacion(
                apoderado, estudiante, FECHA, bloque, "Cita médica", null);
        entidad.setId(id);
        entidad.setEstado(estado);
        return entidad;
    }

    private Asistencia asistencia(Long id, Asistencia.Estado estado) {
        Asistencia entidad = new Asistencia(matricula, bloque, FECHA);
        entidad.setId(id);
        entidad.setEstado(estado);
        return entidad;
    }
}
