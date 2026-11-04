package com.educa360.backend.service;

import com.educa360.backend.dto.AsistenciaBulkRequest;
import com.educa360.backend.dto.AsistenciaClaseResponse;
import com.educa360.backend.dto.AsistenciaItemRequest;
import com.educa360.backend.dto.AsistenciaResumenResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.HorarioBloque;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link AsistenciaService} sin Spring ni base de datos.
 *
 * Cubre la derivación de TARDANZA con reloj falso, la validación de día,
 * bimestre y matrícula, el upsert parcial, la protección de JUSTIFICADA y el
 * cálculo del porcentaje.
 */
@ExtendWith(MockitoExtension.class)
class AsistenciaServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 9); // lunes
    private static final LocalDate INICIO_BIMESTRE = LocalDate.of(2026, 3, 2);
    private static final LocalDate FIN_BIMESTRE = LocalDate.of(2026, 5, 15);
    private static final LocalTime HORA_INICIO = LocalTime.of(8, 0);
    private static final LocalTime HORA_FIN = LocalTime.of(9, 0);
    private static final ZoneId ZONA = ZoneId.of("UTC");

    @Mock
    private AsistenciaRepository asistenciaRepository;

    @Mock
    private CursoRepository cursoRepository;

    @Mock
    private HorarioBloqueRepository horarioRepository;

    @Mock
    private BimestreRepository bimestreRepository;

    @Mock
    private DocenteRepository docenteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private MatriculaRepository matriculaRepository;

    @Mock
    private MatriculaService matriculaService;

    @Mock
    private ApoderadoRepository apoderadoRepository;

    @Mock
    private ApoderadoEstudianteRepository vinculoRepository;

    @Mock
    private TicketJustificacionRepository ticketRepository;

    private User userDocente;
    private Docente docente;
    private AnioEscolar anio;
    private Bimestre bimestre;
    private Seccion seccion;
    private Curso curso;
    private HorarioBloque bloque;
    private User userAlumno;
    private Estudiante estudiante;
    private Matricula matricula;

    private AsistenciaService servicio;

    @BeforeEach
    void setUp() {
        userDocente = user("dana@educa360.pe", User.Role.DOCENTE);
        docente = docente(1L, userDocente);
        anio = anio(10L, 2026);
        bimestre = bimestre(20L, anio, Bimestre.Estado.ABIERTO);
        seccion = seccion(30L, anio);
        curso = curso(40L, docente, seccion);
        bloque = bloque(50L, curso, DiaSemana.LUNES, HORA_INICIO, HORA_FIN);
        userAlumno = user("alma@educa360.pe", User.Role.ESTUDIANTE);
        estudiante = estudiante(60L, userAlumno);
        matricula = matricula(70L, estudiante, anio, seccion);
    }

    @Test
    @DisplayName("Antes del umbral de 15 minutos, el presente queda PRESENTE")
    void presenteAntesDelUmbral() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        prepararDocenteYClase();
        when(asistenciaRepository.findByMatriculaIdAndBloqueIdAndFecha(70L, 50L, FECHA))
                .thenReturn(Optional.empty());
        AtomicReference<Asistencia> guardada = new AtomicReference<>();
        when(asistenciaRepository.save(any(Asistencia.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return guardada.get();
        });
        when(asistenciaRepository.findByBloqueIdAndFecha(50L, FECHA))
                .thenAnswer(inv -> guardada.get() == null
                        ? List.of() : List.of(guardada.get()));

        AsistenciaClaseResponse respuesta = servicio.registrar("dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(70L, true, null))));

        assertEquals(Asistencia.Estado.PRESENTE, guardada.get().getEstado());
        assertEquals(Asistencia.Estado.PRESENTE, respuesta.asistencias().get(0).estado());
    }

    @Test
    @DisplayName("Después de 15 minutos, el presente queda TARDANZA")
    void tardanzaDespuesDelUmbral() {
        servicio = servicioEn(instante("2026-03-09T08:16:00Z"));
        prepararDocenteYClase();
        when(asistenciaRepository.findByMatriculaIdAndBloqueIdAndFecha(70L, 50L, FECHA))
                .thenReturn(Optional.empty());
        AtomicReference<Asistencia> guardada = new AtomicReference<>();
        when(asistenciaRepository.save(any(Asistencia.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return guardada.get();
        });
        when(asistenciaRepository.findByBloqueIdAndFecha(50L, FECHA))
                .thenAnswer(inv -> guardada.get() == null
                        ? List.of() : List.of(guardada.get()));

        servicio.registrar("dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(70L, true, null))));

        assertEquals(Asistencia.Estado.TARDANZA, guardada.get().getEstado());
    }

    @Test
    @DisplayName("Si el alumno no asistió, queda FALTA")
    void faltaCuandoNoAsistio() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        prepararDocenteYClase();
        when(asistenciaRepository.findByMatriculaIdAndBloqueIdAndFecha(70L, 50L, FECHA))
                .thenReturn(Optional.empty());
        AtomicReference<Asistencia> guardada = new AtomicReference<>();
        when(asistenciaRepository.save(any(Asistencia.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return guardada.get();
        });
        when(asistenciaRepository.findByBloqueIdAndFecha(50L, FECHA))
                .thenAnswer(inv -> guardada.get() == null
                        ? List.of() : List.of(guardada.get()));

        servicio.registrar("dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(70L, false, "Sin aviso"))));

        assertEquals(Asistencia.Estado.FALTA, guardada.get().getEstado());
        assertEquals("Sin aviso", guardada.get().getObservacion());
    }

    @Test
    @DisplayName("Una fecha pasada asume PRESENTE: no se reconstruye la hora de llegada")
    void fechaPasadaAsumePresente() {
        servicio = servicioEn(instante("2026-03-16T08:16:00Z"));
        prepararDocenteYClase();
        when(asistenciaRepository.findByMatriculaIdAndBloqueIdAndFecha(70L, 50L, FECHA))
                .thenReturn(Optional.empty());
        AtomicReference<Asistencia> guardada = new AtomicReference<>();
        when(asistenciaRepository.save(any(Asistencia.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return guardada.get();
        });
        when(asistenciaRepository.findByBloqueIdAndFecha(50L, FECHA))
                .thenAnswer(inv -> guardada.get() == null
                        ? List.of() : List.of(guardada.get()));

        servicio.registrar("dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(70L, true, null))));

        assertEquals(Asistencia.Estado.PRESENTE, guardada.get().getEstado());
    }

    @Test
    @DisplayName("No se registra asistencia de una fecha futura")
    void rechazaFechaFutura() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        prepararDocenteYClase();

        assertThrows(BadRequestException.class, () -> servicio.registrar(
                "dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA.plusWeeks(1),
                        List.of(new AsistenciaItemRequest(70L, true, null)))));
    }

    @Test
    @DisplayName("La fecha debe caer en el día del bloque")
    void rechazaDiaIncorrecto() {
        servicio = servicioEn(instante("2026-03-10T08:05:00Z"));
        prepararDocenteYClase();

        assertThrows(BadRequestException.class, () -> servicio.registrar(
                "dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, LocalDate.of(2026, 3, 10),
                        List.of(new AsistenciaItemRequest(70L, true, null)))));
    }

    @Test
    @DisplayName("No se registra asistencia en un bimestre CERRADO")
    void rechazaBimestreCerrado() {
        bimestre.setEstado(Bimestre.Estado.CERRADO);
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        prepararDocenteYClase();

        assertThrows(ConflictException.class, () -> servicio.registrar(
                "dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(70L, true, null)))));
    }

    @Test
    @DisplayName("No se registra asistencia de un alumno de otra sección")
    void rechazaMatriculaDeOtraSeccion() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        prepararDocenteYClase();
        Seccion otra = seccion(31L, anio);
        Matricula ajena = matricula(71L, estudiante, anio, otra);
        when(matriculaRepository.findById(71L)).thenReturn(Optional.of(ajena));

        assertThrows(NotFoundException.class, () -> servicio.registrar(
                "dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(71L, true, null)))));
    }

    @Test
    @DisplayName("El docente no puede pisar una asistencia JUSTIFICADA")
    void rechazaJustificada() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        prepararDocenteYClase();
        Asistencia justificada = asistencia(80L, matricula, bloque, FECHA,
                Asistencia.Estado.JUSTIFICADA);
        when(asistenciaRepository.findByMatriculaIdAndBloqueIdAndFecha(70L, 50L, FECHA))
                .thenReturn(Optional.of(justificada));

        assertThrows(ConflictException.class, () -> servicio.registrar(
                "dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(70L, true, null)))));
    }

    @Test
    @DisplayName("Una falta registrada después de aprobar el ticket nace JUSTIFICADA")
    void faltaNaceJustificadaSiHayTicketAprobado() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        prepararDocenteYClase();
        when(asistenciaRepository.findByMatriculaIdAndBloqueIdAndFecha(70L, 50L, FECHA))
                .thenReturn(Optional.empty());
        TicketJustificacion ticket = ticket(90L, matricula.getEstudiante());
        when(ticketRepository.buscarCoberturaAprobada(60L, FECHA, 50L))
                .thenReturn(List.of(ticket));
        AtomicReference<Asistencia> guardada = new AtomicReference<>();
        when(asistenciaRepository.save(any(Asistencia.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return guardada.get();
        });
        when(asistenciaRepository.findByBloqueIdAndFecha(50L, FECHA))
                .thenAnswer(inv -> guardada.get() == null
                        ? List.of() : List.of(guardada.get()));

        servicio.registrar("dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(70L, false, null))));

        assertEquals(Asistencia.Estado.JUSTIFICADA, guardada.get().getEstado());
        assertEquals(ticket, guardada.get().getTicket());
    }

    @Test
    @DisplayName("Una edición tardía no degrada un PRESENTE ya registrado")
    void noDegradaPresenteATardanza() {
        servicio = servicioEn(instante("2026-03-09T08:16:00Z"));
        prepararDocenteYClase();
        Asistencia presente = asistencia(80L, matricula, bloque, FECHA,
                Asistencia.Estado.PRESENTE);
        when(asistenciaRepository.findByMatriculaIdAndBloqueIdAndFecha(70L, 50L, FECHA))
                .thenReturn(Optional.of(presente));
        when(asistenciaRepository.save(any(Asistencia.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(asistenciaRepository.findByBloqueIdAndFecha(50L, FECHA))
                .thenReturn(List.of(presente));

        servicio.registrar("dana@educa360.pe", 40L,
                new AsistenciaBulkRequest(50L, FECHA,
                        List.of(new AsistenciaItemRequest(70L, true, null))));

        assertEquals(Asistencia.Estado.PRESENTE, presente.getEstado());
    }

    @Test
    @DisplayName("La hoja de clase incluye a los alumnos todavía sin registro")
    void claseIncluyeAlumnosSinRegistro() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        prepararDocenteYClase();
        Estudiante otro = estudiante(61L, user("beto@educa360.pe", User.Role.ESTUDIANTE));
        Matricula otraMatricula = matricula(72L, otro, anio, seccion);
        Asistencia registrada = asistencia(80L, matricula, bloque, FECHA,
                Asistencia.Estado.PRESENTE);
        when(matriculaRepository.findBySeccionId(30L))
                .thenReturn(List.of(matricula, otraMatricula));
        when(asistenciaRepository.findByBloqueIdAndFecha(50L, FECHA))
                .thenReturn(List.of(registrada));

        AsistenciaClaseResponse respuesta =
                servicio.listarClase("dana@educa360.pe", 40L, 50L, FECHA);

        assertEquals(2, respuesta.asistencias().size());
        for (var fila : respuesta.asistencias()) {
            if (fila.matriculaId().equals(72L)) {
                assertNull(fila.estado());
            } else {
                assertEquals(Asistencia.Estado.PRESENTE, fila.estado());
            }
        }
    }

    @Test
    @DisplayName("El resumen calcula efectivas, faltas y porcentaje")
    void resumenCalculaPorcentaje() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        when(userRepository.findByEmail("alma@educa360.pe")).thenReturn(Optional.of(userAlumno));
        when(estudianteRepository.findByUser(userAlumno)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(60L)).thenReturn(Optional.of(matricula));

        Asistencia presente = asistencia(80L, matricula, bloque, FECHA,
                Asistencia.Estado.PRESENTE);
        Asistencia tardanza = asistencia(81L, matricula, bloque, FECHA.plusWeeks(1),
                Asistencia.Estado.TARDANZA);
        Asistencia falta = asistencia(82L, matricula, bloque, FECHA.plusWeeks(2),
                Asistencia.Estado.FALTA);
        when(asistenciaRepository.findByMatriculaIdOrderByFechaAscBloqueHoraInicioAsc(70L))
                .thenReturn(List.of(presente, tardanza, falta));

        AsistenciaResumenResponse respuesta =
                servicio.resumenDelEstudiante("alma@educa360.pe", null, null);

        assertEquals(3, respuesta.total());
        assertEquals(1, respuesta.presentes());
        assertEquals(1, respuesta.tardanzas());
        assertEquals(0, respuesta.justificadas());
        assertEquals(1, respuesta.faltas());
        assertEquals(2, respuesta.efectivas());
        assertEquals(new BigDecimal("66.67"), respuesta.porcentaje());
    }

    @Test
    @DisplayName("El resumen de una matrícula sin registros da 0.00%")
    void resumenSinRegistros() {
        servicio = servicioEn(instante("2026-03-09T08:05:00Z"));
        when(userRepository.findByEmail("alma@educa360.pe")).thenReturn(Optional.of(userAlumno));
        when(estudianteRepository.findByUser(userAlumno)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(60L)).thenReturn(Optional.of(matricula));
        when(asistenciaRepository.findByMatriculaIdOrderByFechaAscBloqueHoraInicioAsc(70L))
                .thenReturn(List.of());

        AsistenciaResumenResponse respuesta =
                servicio.resumenDelEstudiante("alma@educa360.pe", null, null);

        assertEquals(0, respuesta.total());
        assertEquals(new BigDecimal("0.00"), respuesta.porcentaje());
    }

    // ==================== Helpers ====================

    private AsistenciaService servicioEn(Instant instante) {
        return new AsistenciaService(asistenciaRepository, cursoRepository, horarioRepository,
                bimestreRepository, docenteRepository, userRepository, estudianteRepository,
                matriculaRepository, matriculaService, apoderadoRepository, vinculoRepository,
                ticketRepository, Clock.fixed(instante, ZONA));
    }

    private void prepararDocenteYClase() {
        lenient().when(userRepository.findByEmail("dana@educa360.pe"))
                .thenReturn(Optional.of(userDocente));
        lenient().when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));
        lenient().when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
        lenient().when(horarioRepository.findById(50L)).thenReturn(Optional.of(bloque));
        lenient().when(bimestreRepository.findByAnioIdOrderByNumeroAsc(10L))
                .thenReturn(List.of(bimestre));
        lenient().when(matriculaRepository.findById(70L)).thenReturn(Optional.of(matricula));
        lenient().when(matriculaRepository.findBySeccionId(30L)).thenReturn(List.of(matricula));
    }

    private Instant instante(String texto) {
        return Instant.parse(texto);
    }

    private User user(String email, User.Role rol) {
        User user = new User();
        user.setEmail(email);
        user.setNombres("Dana");
        user.setApellidos("Perez");
        user.setDni("10000001");
        user.setRol(rol);
        user.setActivo(true);
        return user;
    }

    private Docente docente(Long id, User user) {
        Docente docente = new Docente(user, "Lic. en Matemáticas");
        docente.setId(id);
        return docente;
    }

    private AnioEscolar anio(Long id, int anio) {
        AnioEscolar entidad = new AnioEscolar(anio, "Año Escolar " + anio,
                LocalDate.of(anio, 1, 1));
        entidad.setId(id);
        entidad.setEstado(AnioEscolar.Estado.ABIERTO);
        return entidad;
    }

    private Bimestre bimestre(Long id, AnioEscolar anio, Bimestre.Estado estado) {
        Bimestre entidad = new Bimestre(anio, 1, INICIO_BIMESTRE, FIN_BIMESTRE);
        entidad.setId(id);
        entidad.setEstado(estado);
        return entidad;
    }

    private Seccion seccion(Long id, AnioEscolar anio) {
        Seccion entidad = new Seccion(3, "A", "A-101", null, anio);
        entidad.setId(id);
        return entidad;
    }

    private Curso curso(Long id, Docente docente, Seccion seccion) {
        Materia materia = new Materia("Matemática", null);
        materia.setId(5L);
        Curso entidad = new Curso(docente, materia, seccion);
        entidad.setId(id);
        return entidad;
    }

    private HorarioBloque bloque(Long id, Curso curso, DiaSemana dia,
                                 LocalTime inicio, LocalTime fin) {
        HorarioBloque entidad = new HorarioBloque(curso, dia, inicio, fin);
        entidad.setId(id);
        return entidad;
    }

    private Estudiante estudiante(Long id, User user) {
        Estudiante entidad = new Estudiante(user, LocalDate.of(2011, 3, 3));
        entidad.setId(id);
        entidad.setEstado(Estudiante.EstadoEstudiante.ACTIVO);
        return entidad;
    }

    private Matricula matricula(Long id, Estudiante estudiante, AnioEscolar anio,
                                Seccion seccion) {
        Matricula entidad = new Matricula(estudiante, anio, seccion,
                LocalDate.of(2026, 2, 20));
        entidad.setId(id);
        return entidad;
    }

    private Asistencia asistencia(Long id, Matricula matricula, HorarioBloque bloque,
                                  LocalDate fecha, Asistencia.Estado estado) {
        Asistencia entidad = new Asistencia(matricula, bloque, fecha);
        entidad.setId(id);
        entidad.setEstado(estado);
        return entidad;
    }

    private TicketJustificacion ticket(Long id, Estudiante estudiante) {
        User user = user("lucia@educa360.pe", User.Role.APODERADO);
        Apoderado apoderado = new Apoderado(user);
        apoderado.setId(91L);
        TicketJustificacion entidad = new TicketJustificacion(
                apoderado, estudiante, FECHA, bloque, "Cita médica", null);
        entidad.setId(id);
        entidad.setEstado(TicketJustificacion.Estado.APROBADO);
        return entidad;
    }
}
