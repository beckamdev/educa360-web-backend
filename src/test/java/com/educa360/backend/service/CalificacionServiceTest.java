package com.educa360.backend.service;

import com.educa360.backend.dto.ActaCursoResponse;
import com.educa360.backend.dto.CalificacionBulkRequest;
import com.educa360.backend.dto.CalificacionItemRequest;
import com.educa360.backend.dto.CalificacionResumenResponse;
import com.educa360.backend.dto.CalificacionUnidadResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Calificacion;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.Unidad;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.CalificacionRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.UnidadRepository;
import com.educa360.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
 * Reglas de {@link CalificacionService} sin Spring ni base de datos.
 *
 * Cubre validaciones de nota, bimestre y matrícula, upsert parcial,
 * visibilidad de unidades en borrador, promedios y acta.
 */
@ExtendWith(MockitoExtension.class)
class CalificacionServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);
    private static final LocalDate FIN = LocalDate.of(2026, 5, 15);

    @Mock
    private CalificacionRepository calificacionRepository;

    @Mock
    private UnidadRepository unidadRepository;

    @Mock
    private CursoRepository cursoRepository;

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
    private NotificacionService notificacionService;

    private User userDocente;
    private Docente docente;
    private AnioEscolar anio;
    private Bimestre bimestre;
    private Seccion seccion;
    private Curso curso;
    private Unidad unidad;
    private User userAlumno;
    private Estudiante estudiante;
    private Matricula matricula;

    private CalificacionService servicio;

    @BeforeEach
    void setUp() {
        userDocente = user("dana@educa360.pe", User.Role.DOCENTE, "Dana");
        docente = docente(1L, userDocente);
        anio = anio(10L, 2026);
        bimestre = bimestre(20L, anio, 1, Bimestre.Estado.ABIERTO);
        seccion = seccion(30L, anio);
        curso = curso(40L, docente, seccion);
        unidad = unidad(50L, curso, bimestre, 1, Unidad.Estado.PUBLICADA);
        userAlumno = user("alma@educa360.pe", User.Role.ESTUDIANTE, "Alma");
        estudiante = estudiante(60L, userAlumno);
        matricula = matricula(70L, estudiante, anio, seccion);

        servicio = new CalificacionService(calificacionRepository, unidadRepository,
                cursoRepository, bimestreRepository, docenteRepository, userRepository,
                estudianteRepository, matriculaRepository, matriculaService,
                apoderadoRepository, vinculoRepository, notificacionService);
    }

    @Test
    @DisplayName("El docente registra una nota y la hoja devuelve al alumno")
    void registraNota() {
        prepararDocenteYUnidad();
        when(matriculaRepository.findById(70L)).thenReturn(Optional.of(matricula));
        when(calificacionRepository.findByMatriculaIdAndUnidadIdAndCriterio(
                70L, 50L, "Examen parcial")).thenReturn(Optional.empty());
        AtomicReference<Calificacion> guardada = new AtomicReference<>();
        when(calificacionRepository.save(any(Calificacion.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return guardada.get();
        });
        when(calificacionRepository.findByUnidadId(50L))
                .thenAnswer(inv -> guardada.get() == null
                        ? List.of() : List.of(guardada.get()));
        when(matriculaRepository.findBySeccionId(30L)).thenReturn(List.of(matricula));

        CalificacionUnidadResponse respuesta = servicio.registrar(
                "dana@educa360.pe", 50L,
                new CalificacionBulkRequest(List.of(new CalificacionItemRequest(
                        70L, "  Examen parcial  ", new BigDecimal("15.50")))));

        assertNotNull(guardada.get());
        assertEquals("Examen parcial", guardada.get().getCriterio());
        assertEquals(new BigDecimal("15.50"), guardada.get().getNota());
        assertEquals(docente, guardada.get().getRegistradoPor());
        assertEquals(1, respuesta.alumnos().size());
        assertEquals(1, respuesta.alumnos().get(0).calificaciones().size());
    }

    @Test
    @DisplayName("El upsert actualiza la nota existente sin crear otra fila")
    void upsertActualizaNota() {
        prepararDocenteYUnidad();
        when(matriculaRepository.findById(70L)).thenReturn(Optional.of(matricula));
        Calificacion existente = calificacion(80L, matricula, unidad,
                "Examen parcial", "10.00");
        when(calificacionRepository.findByMatriculaIdAndUnidadIdAndCriterio(
                70L, 50L, "Examen parcial")).thenReturn(Optional.of(existente));
        when(calificacionRepository.save(any(Calificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(calificacionRepository.findByUnidadId(50L)).thenReturn(List.of(existente));
        when(matriculaRepository.findBySeccionId(30L)).thenReturn(List.of(matricula));

        servicio.registrar("dana@educa360.pe", 50L,
                new CalificacionBulkRequest(List.of(new CalificacionItemRequest(
                        70L, "Examen parcial", new BigDecimal("16.00")))));

        assertEquals(80L, existente.getId());
        assertEquals(new BigDecimal("16.00"), existente.getNota());
        verify(calificacionRepository).save(existente);
    }

    @Test
    @DisplayName("No se califica en un bimestre CERRADO")
    void rechazaBimestreCerrado() {
        bimestre.setEstado(Bimestre.Estado.CERRADO);
        prepararDocenteYUnidad();

        assertThrows(ConflictException.class, () -> servicio.registrar(
                "dana@educa360.pe", 50L,
                new CalificacionBulkRequest(List.of(new CalificacionItemRequest(
                        70L, "Examen parcial", new BigDecimal("15.00"))))));
        verify(calificacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("No se califica a un alumno de otra sección")
    void rechazaMatriculaDeOtraSeccion() {
        prepararDocenteYUnidad();
        Seccion otra = seccion(31L, anio);
        Matricula ajena = matricula(71L, estudiante, anio, otra);
        when(matriculaRepository.findById(71L)).thenReturn(Optional.of(ajena));

        assertThrows(NotFoundException.class, () -> servicio.registrar(
                "dana@educa360.pe", 50L,
                new CalificacionBulkRequest(List.of(new CalificacionItemRequest(
                        71L, "Examen parcial", new BigDecimal("15.00"))))));
    }

    @Test
    @DisplayName("La nota fuera de rango o con más de dos decimales se rechaza")
    void rechazaNotaInvalida() {
        prepararDocenteYUnidad();

        assertThrows(BadRequestException.class, () -> servicio.registrar(
                "dana@educa360.pe", 50L,
                new CalificacionBulkRequest(List.of(new CalificacionItemRequest(
                        70L, "Examen parcial", new BigDecimal("20.01"))))));

        assertThrows(BadRequestException.class, () -> servicio.registrar(
                "dana@educa360.pe", 50L,
                new CalificacionBulkRequest(List.of(new CalificacionItemRequest(
                        70L, "Examen parcial", new BigDecimal("15.555"))))));
    }

    @Test
    @DisplayName("La lista no puede repetir matrícula y criterio")
    void rechazaCriterioDuplicado() {
        prepararDocenteYUnidad();

        assertThrows(BadRequestException.class, () -> servicio.registrar(
                "dana@educa360.pe", 50L,
                new CalificacionBulkRequest(List.of(
                        new CalificacionItemRequest(70L, "Examen parcial", new BigDecimal("10.00")),
                        new CalificacionItemRequest(70L, " Examen parcial ", new BigDecimal("12.00"))))));
    }

    @Test
    @DisplayName("El alumno sólo ve notas de unidades PUBLICADA")
    void resumenSoloVePublicadas() {
        Unidad borrador = unidad(51L, curso, bimestre, 2, Unidad.Estado.BORRADOR);
        Calificacion publicada = calificacion(80L, matricula, unidad,
                "Examen parcial", "16.00");
        Calificacion oculta = calificacion(81L, matricula, borrador,
                "Práctica 1", "05.00");

        when(userRepository.findByEmail("alma@educa360.pe")).thenReturn(Optional.of(userAlumno));
        when(estudianteRepository.findByUser(userAlumno)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(60L)).thenReturn(Optional.of(matricula));
        when(calificacionRepository
                .findByMatriculaIdOrderByUnidadBimestreNumeroAscUnidadNumeroAscCriterioAsc(70L))
                .thenReturn(List.of(publicada, oculta));

        CalificacionResumenResponse respuesta =
                servicio.resumenDelEstudiante("alma@educa360.pe", null, null);

        assertEquals(1, respuesta.detalle().size());
        assertEquals("Examen parcial", respuesta.detalle().get(0).criterio());
        assertEquals(new BigDecimal("16.00"), respuesta.promedioGeneral());
    }

    @Test
    @DisplayName("El promedio anual usa los bimestres, no todas las unidades juntas")
    void promedioAnualUsaBimestres() {
        Bimestre segundo = bimestre(21L, anio, 2, Bimestre.Estado.ABIERTO);
        Unidad u1 = unidad(50L, curso, bimestre, 1, Unidad.Estado.PUBLICADA);
        Unidad u2 = unidad(51L, curso, segundo, 1, Unidad.Estado.PUBLICADA);
        Unidad u3 = unidad(52L, curso, segundo, 2, Unidad.Estado.PUBLICADA);

        when(userRepository.findByEmail("alma@educa360.pe")).thenReturn(Optional.of(userAlumno));
        when(estudianteRepository.findByUser(userAlumno)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(60L)).thenReturn(Optional.of(matricula));
        when(calificacionRepository
                .findByMatriculaIdOrderByUnidadBimestreNumeroAscUnidadNumeroAscCriterioAsc(70L))
                .thenReturn(List.of(
                        calificacion(80L, matricula, u1, "Examen", "10.00"),
                        calificacion(81L, matricula, u2, "Examen", "20.00"),
                        calificacion(82L, matricula, u3, "Examen", "20.00")));

        CalificacionResumenResponse respuesta =
                servicio.resumenDelEstudiante("alma@educa360.pe", null, null);

        // B1 = 10.00; B2 = (20 + 20) / 2 = 20.00; anual = (10 + 20) / 2 = 15.00
        assertEquals(3, respuesta.promediosUnidad().size());
        assertEquals(new BigDecimal("15.00"), respuesta.promediosCurso().get(0).promedio());
        assertEquals(new BigDecimal("15.00"), respuesta.promedioGeneral());
    }

    @Test
    @DisplayName("El resumen por bimestre acota las notas y promedios a ese bimestre")
    void resumenPorBimestre() {
        when(userRepository.findByEmail("alma@educa360.pe")).thenReturn(Optional.of(userAlumno));
        when(estudianteRepository.findByUser(userAlumno)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(60L)).thenReturn(Optional.of(matricula));
        when(bimestreRepository.findById(20L)).thenReturn(Optional.of(bimestre));
        when(calificacionRepository
                .findByMatriculaIdAndUnidadBimestreIdOrderByUnidadNumeroAscCriterioAsc(70L, 20L))
                .thenReturn(List.of(calificacion(80L, matricula, unidad,
                        "Examen", "16.00")));

        CalificacionResumenResponse respuesta =
                servicio.resumenDelEstudiante("alma@educa360.pe", null, 20L);

        assertEquals(20L, respuesta.bimestreId());
        assertEquals("Bimestre I", respuesta.bimestreNombre());
        assertEquals(1, respuesta.detalle().size());
        assertEquals(new BigDecimal("16.00"), respuesta.promedioGeneral());
    }

    @Test
    @DisplayName("El acta arma alumno × unidades y promedia el bimestre")
    void actaArmaFilasYPromedios() {
        Unidad u2 = unidad(51L, curso, bimestre, 2, Unidad.Estado.PUBLICADA);
        User userBeto = user("beto@educa360.pe", User.Role.ESTUDIANTE, "Beto");
        Estudiante beto = estudiante(61L, userBeto);
        Matricula matriculaBeto = matricula(71L, beto, anio, seccion);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(userDocente));
        when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));
        when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
        when(bimestreRepository.findById(20L)).thenReturn(Optional.of(bimestre));
        when(unidadRepository.findByCursoIdAndBimestreIdOrderByNumeroAsc(40L, 20L))
                .thenReturn(List.of(unidad, u2));
        when(calificacionRepository
                .findByUnidadCursoIdAndUnidadBimestreIdOrderByUnidadNumeroAscCriterioAsc(40L, 20L))
                .thenReturn(List.of(
                        calificacion(80L, matricula, unidad, "Examen", "10.00"),
                        calificacion(81L, matricula, u2, "Examen", "20.00"),
                        calificacion(82L, matriculaBeto, unidad, "Examen", "11.00")));
        when(matriculaRepository.findBySeccionId(30L))
                .thenReturn(List.of(matricula, matriculaBeto));

        ActaCursoResponse acta = servicio.actaDelDocente("dana@educa360.pe", 40L, 20L);

        assertEquals(2, acta.alumnos().size());
        assertEquals(new BigDecimal("15.00"), acta.alumnos().get(0).promedioBimestre());
        assertEquals(new BigDecimal("11.00"), acta.alumnos().get(1).promedioBimestre());
        assertNull(acta.alumnos().get(1).unidades().get(1).promedio());
        assertEquals(new BigDecimal("13.00"), acta.promedioCurso());
    }

    @Test
    @DisplayName("No se elimina una nota de un bimestre CERRADO")
    void rechazaEliminarEnBimestreCerrado() {
        bimestre.setEstado(Bimestre.Estado.CERRADO);
        Calificacion existente = calificacion(80L, matricula, unidad,
                "Examen", "10.00");
        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(userDocente));
        when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));
        when(calificacionRepository.findById(80L)).thenReturn(Optional.of(existente));

        assertThrows(ConflictException.class,
                () -> servicio.eliminar("dana@educa360.pe", 80L));
        verify(calificacionRepository, never()).delete(any());
    }

    // ==================== Helpers ====================

    private void prepararDocenteYUnidad() {
        lenient().when(userRepository.findByEmail("dana@educa360.pe"))
                .thenReturn(Optional.of(userDocente));
        lenient().when(docenteRepository.findByUser(userDocente))
                .thenReturn(Optional.of(docente));
        lenient().when(unidadRepository.findById(50L)).thenReturn(Optional.of(unidad));
    }

    private User user(String email, User.Role rol, String nombres) {
        User user = new User();
        user.setEmail(email);
        user.setNombres(nombres);
        user.setApellidos("Luna");
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

    private Bimestre bimestre(Long id, AnioEscolar anio, int numero, Bimestre.Estado estado) {
        Bimestre entidad = new Bimestre(anio, numero, INICIO, FIN);
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

    private Unidad unidad(Long id, Curso curso, Bimestre bimestre, int numero,
                          Unidad.Estado estado) {
        Unidad entidad = new Unidad(curso, bimestre, numero, "Unidad " + numero);
        entidad.setId(id);
        entidad.setEstado(estado);
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

    private Calificacion calificacion(Long id, Matricula matricula, Unidad unidad,
                                      String criterio, String nota) {
        Calificacion entidad = new Calificacion(matricula, unidad, criterio);
        entidad.setId(id);
        entidad.setNota(new BigDecimal(nota));
        return entidad;
    }
}
