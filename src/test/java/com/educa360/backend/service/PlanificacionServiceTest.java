package com.educa360.backend.service;

import com.educa360.backend.dto.ContenidoRequest;
import com.educa360.backend.dto.ContenidoResponse;
import com.educa360.backend.dto.ReordenarContenidosRequest;
import com.educa360.backend.dto.UnidadCreateRequest;
import com.educa360.backend.dto.UnidadResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Contenido;
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
import com.educa360.backend.repository.ContenidoRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.UnidadRepository;
import com.educa360.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link PlanificacionService} sin Spring ni base de datos.
 *
 * Cubre lo que los tests de API no pueden ejecutar localmente sin Docker:
 * numeración automática, bimestre del mismo año, estados cerrados, fechas,
 * orden de contenidos, reordenamiento en dos pasadas y visibilidad.
 */
@ExtendWith(MockitoExtension.class)
class PlanificacionServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);
    private static final LocalDate FIN = LocalDate.of(2026, 5, 15);

    @Mock
    private UnidadRepository unidadRepository;

    @Mock
    private ContenidoRepository contenidoRepository;

    @Mock
    private CursoRepository cursoRepository;

    @Mock
    private BimestreRepository bimestreRepository;

    @Mock
    private CalificacionRepository calificacionRepository;

    @Mock
    private DocenteRepository docenteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private MatriculaService matriculaService;

    @Mock
    private ApoderadoRepository apoderadoRepository;

    @Mock
    private ApoderadoEstudianteRepository vinculoRepository;

    private PlanificacionService servicio;

    @BeforeEach
    void setUp() {
        servicio = new PlanificacionService(unidadRepository, contenidoRepository,
                cursoRepository, bimestreRepository, calificacionRepository,
                docenteRepository, userRepository, estudianteRepository,
                matriculaService, apoderadoRepository, vinculoRepository);
    }

    @Test
    @DisplayName("La unidad nace BORRADOR y con el siguiente número del curso/bimestre")
    void unidadNaceBorradorConNumeroAutomatico() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Curso curso = curso(40L, docente, anio);
        Unidad existente = unidad(50L, curso, bimestre, 1);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
        when(bimestreRepository.findById(20L)).thenReturn(Optional.of(bimestre));
        when(unidadRepository.findFirstByCursoIdAndBimestreIdOrderByNumeroDesc(40L, 20L))
                .thenReturn(Optional.of(existente));
        when(unidadRepository.save(any(Unidad.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UnidadResponse respuesta = servicio.crear("dana@educa360.pe", 40L,
                new UnidadCreateRequest(20L, "Unidad 2", "Cuerpos geométricos",
                        INICIO, FIN));

        assertEquals(2, respuesta.numero());
        assertEquals(Unidad.Estado.BORRADOR, respuesta.estado());
        assertEquals("Matemática", respuesta.materiaNombre());
        assertEquals("3°A", respuesta.seccionEtiqueta());
        assertTrue(respuesta.contenidos().isEmpty());
    }

    @Test
    @DisplayName("No se crea una unidad en un bimestre de otro año")
    void unidadRechazaBimestreDeOtroAnio() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        AnioEscolar otroAnio = new AnioEscolar(2027, "Año Escolar 2027",
                LocalDate.of(2027, 3, 1));
        otroAnio.setId(11L);
        Bimestre ajeno = bimestre(21L, otroAnio);
        Curso curso = curso(40L, docente, anio);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
        when(bimestreRepository.findById(21L)).thenReturn(Optional.of(ajeno));

        assertThrows(ConflictException.class, () -> servicio.crear("dana@educa360.pe", 40L,
                new UnidadCreateRequest(21L, "Unidad", null, null, null)));
    }

    @Test
    @DisplayName("No se planifica en un bimestre CERRADO")
    void unidadRechazaBimestreCerrado() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        bimestre.setEstado(Bimestre.Estado.CERRADO);
        Curso curso = curso(40L, docente, anio);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
        when(bimestreRepository.findById(20L)).thenReturn(Optional.of(bimestre));

        assertThrows(ConflictException.class, () -> servicio.crear("dana@educa360.pe", 40L,
                new UnidadCreateRequest(20L, "Unidad", null, null, null)));
    }

    @Test
    @DisplayName("Las fechas de la unidad deben caer dentro del bimestre")
    void unidadRechazaFechasFueraDelBimestre() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Curso curso = curso(40L, docente, anio);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
        when(bimestreRepository.findById(20L)).thenReturn(Optional.of(bimestre));

        assertThrows(BadRequestException.class, () -> servicio.crear("dana@educa360.pe", 40L,
                new UnidadCreateRequest(20L, "Unidad", null,
                        LocalDate.of(2026, 2, 1), null)));
    }

    @Test
    @DisplayName("El contenido se agrega al final con el siguiente orden")
    void contenidoTomaSiguienteOrden() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Curso curso = curso(40L, docente, anio);
        Unidad unidad = unidad(50L, curso, bimestre, 1);
        Contenido existente = contenido(61L, unidad, 2);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(unidadRepository.findById(50L)).thenReturn(Optional.of(unidad));
        when(contenidoRepository.findFirstByUnidadIdOrderByOrdenDesc(50L))
                .thenReturn(Optional.of(existente));
        when(contenidoRepository.save(any(Contenido.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ContenidoResponse respuesta = servicio.crearContenido("dana@educa360.pe", 50L,
                new ContenidoRequest("Tema 3", "Descripción", LocalDate.of(2026, 3, 10)));

        assertEquals(3, respuesta.orden());
        assertEquals("Tema 3", respuesta.titulo());
        assertEquals(LocalDate.of(2026, 3, 10), respuesta.fechaPrevista());
    }

    @Test
    @DisplayName("Reordenar asigna 1..n sin violar el unique")
    void reordenarAsignaOrdenFinal() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Curso curso = curso(40L, docente, anio);
        Unidad unidad = unidad(50L, curso, bimestre, 1);
        Contenido c1 = contenido(61L, unidad, 1);
        Contenido c2 = contenido(62L, unidad, 2);
        Contenido c3 = contenido(63L, unidad, 3);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(unidadRepository.findById(50L)).thenReturn(Optional.of(unidad));
        when(contenidoRepository.findByUnidadIdOrderByOrdenAsc(50L))
                .thenReturn(List.of(c1, c2, c3));

        List<ContenidoResponse> respuesta = servicio.reordenar("dana@educa360.pe", 50L,
                new ReordenarContenidosRequest(List.of(63L, 61L, 62L)));

        assertEquals(1, c3.getOrden());
        assertEquals(2, c1.getOrden());
        assertEquals(3, c2.getOrden());
        assertEquals(List.of(63L, 61L, 62L),
                respuesta.stream().map(ContenidoResponse::id).toList());
        verify(contenidoRepository, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    @DisplayName("Reordenar exige exactamente los contenidos de la unidad")
    void reordenarRechazaListaIncompleta() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Curso curso = curso(40L, docente, anio);
        Unidad unidad = unidad(50L, curso, bimestre, 1);
        Contenido c1 = contenido(61L, unidad, 1);
        Contenido c2 = contenido(62L, unidad, 2);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(unidadRepository.findById(50L)).thenReturn(Optional.of(unidad));
        when(contenidoRepository.findByUnidadIdOrderByOrdenAsc(50L))
                .thenReturn(List.of(c1, c2));

        assertThrows(BadRequestException.class, () -> servicio.reordenar("dana@educa360.pe", 50L,
                new ReordenarContenidosRequest(List.of(61L))));
    }

    @Test
    @DisplayName("El estudiante sólo recibe unidades PUBLICADA de su sección")
    void estudianteSoloVePublicadas() {
        User user = user("alma@educa360.pe", User.Role.ESTUDIANTE);
        Estudiante estudiante = estudiante(70L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Seccion seccion = seccion(30L, anio);
        Docente docente = docente(1L, user("dana@educa360.pe", User.Role.DOCENTE));
        Curso curso = new Curso(docente, materia(), seccion);
        curso.setId(40L);
        Matricula matricula = new Matricula(estudiante, anio, seccion,
                LocalDate.of(2026, 2, 20));
        matricula.setId(80L);
        Unidad publicada = unidad(50L, curso, bimestre, 1);
        publicada.setEstado(Unidad.Estado.PUBLICADA);

        when(userRepository.findByEmail("alma@educa360.pe")).thenReturn(Optional.of(user));
        when(estudianteRepository.findByUser(user)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(70L)).thenReturn(Optional.of(matricula));
        when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
        when(unidadRepository.findByCursoIdAndEstadoOrderByBimestreNumeroAscNumeroAsc(
                40L, Unidad.Estado.PUBLICADA)).thenReturn(List.of(publicada));

        List<UnidadResponse> respuesta =
                servicio.listarDelEstudiante("alma@educa360.pe", 40L);

        assertEquals(1, respuesta.size());
        assertEquals(Unidad.Estado.PUBLICADA, respuesta.get(0).estado());
        verify(unidadRepository).findByCursoIdAndEstadoOrderByBimestreNumeroAscNumeroAsc(
                40L, Unidad.Estado.PUBLICADA);
    }

    @Test
    @DisplayName("Un docente no accede a una unidad de un curso ajeno")
    void docenteNoAccedeAUnidadAjena() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        User otroUser = user("otro@educa360.pe", User.Role.DOCENTE);
        Docente otroDocente = docente(2L, otroUser);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Curso ajeno = curso(41L, otroDocente, anio);
        Unidad unidadAjena = unidad(51L, ajeno, bimestre, 1);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(unidadRepository.findById(51L)).thenReturn(Optional.of(unidadAjena));

        assertThrows(NotFoundException.class,
                () -> servicio.buscar("dana@educa360.pe", 51L));
    }

    @Test
    @DisplayName("Un apoderado no accede a un hijo no vinculado")
    void apoderadoNoAccedeAHijoNoVinculado() {
        User user = user("lucia@educa360.pe", User.Role.APODERADO);
        Apoderado apoderado = new Apoderado(user);
        apoderado.setId(90L);

        when(userRepository.findByEmail("lucia@educa360.pe")).thenReturn(Optional.of(user));
        when(apoderadoRepository.findByUser(user)).thenReturn(Optional.of(apoderado));
        when(vinculoRepository.findByApoderadoIdAndEstudianteId(90L, 70L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> servicio.listarDelApoderado(
                "lucia@educa360.pe", 70L, 40L));
    }

    @Test
    @DisplayName("No se despublica una unidad que ya tiene calificaciones")
    void noDespublicaUnidadConCalificaciones() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Curso curso = curso(40L, docente, anio);
        Unidad unidad = unidad(50L, curso, bimestre, 1);
        unidad.setEstado(Unidad.Estado.PUBLICADA);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(unidadRepository.findById(50L)).thenReturn(Optional.of(unidad));
        when(calificacionRepository.existsByUnidadId(50L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> servicio.cambiarEstado(
                "dana@educa360.pe", 50L, Unidad.Estado.BORRADOR));
    }

    @Test
    @DisplayName("No se elimina una unidad que ya tiene calificaciones")
    void noEliminaUnidadConCalificaciones() {
        User user = user("dana@educa360.pe", User.Role.DOCENTE);
        Docente docente = docente(1L, user);
        AnioEscolar anio = anio();
        Bimestre bimestre = bimestre(20L, anio);
        Curso curso = curso(40L, docente, anio);
        Unidad unidad = unidad(50L, curso, bimestre, 1);

        when(userRepository.findByEmail("dana@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(unidadRepository.findById(50L)).thenReturn(Optional.of(unidad));
        when(calificacionRepository.existsByUnidadId(50L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> servicio.eliminar("dana@educa360.pe", 50L));
    }

    // ==================== Helpers ====================

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

    private AnioEscolar anio() {
        AnioEscolar anio = new AnioEscolar(2026, "Año Escolar 2026", INICIO);
        anio.setId(10L);
        anio.setEstado(AnioEscolar.Estado.ABIERTO);
        return anio;
    }

    private Bimestre bimestre(Long id, AnioEscolar anio) {
        Bimestre bimestre = new Bimestre(anio, 1, INICIO, FIN);
        bimestre.setId(id);
        bimestre.setEstado(Bimestre.Estado.ABIERTO);
        return bimestre;
    }

    private Seccion seccion(Long id, AnioEscolar anio) {
        Seccion seccion = new Seccion(3, "A", "A-101", null, anio);
        seccion.setId(id);
        return seccion;
    }

    private Curso curso(Long id, Docente docente, AnioEscolar anio) {
        Curso curso = new Curso(docente, materia(), seccion(30L, anio));
        curso.setId(id);
        return curso;
    }

    private Materia materia() {
        Materia materia = new Materia("Matemática", null);
        materia.setId(5L);
        return materia;
    }

    private Unidad unidad(Long id, Curso curso, Bimestre bimestre, int numero) {
        Unidad unidad = new Unidad(curso, bimestre, numero, "Unidad " + numero);
        unidad.setId(id);
        return unidad;
    }

    private Contenido contenido(Long id, Unidad unidad, int orden) {
        Contenido contenido = new Contenido(unidad, orden, "Tema " + orden);
        contenido.setId(id);
        return contenido;
    }

    private Estudiante estudiante(Long id, User user) {
        Estudiante estudiante = new Estudiante(user, LocalDate.of(2011, 3, 3));
        estudiante.setId(id);
        estudiante.setEstado(Estudiante.EstadoEstudiante.ACTIVO);
        return estudiante;
    }
}
