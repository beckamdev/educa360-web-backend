package com.educa360.backend.service;

import com.educa360.backend.dto.ComunicadoCreateRequest;
import com.educa360.backend.dto.ComunicadoResponse;
import com.educa360.backend.dto.ComunicadoUpdateRequest;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Comunicado;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.Secretaria;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.ComunicadoRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.SeccionRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link ComunicadoService} sin Spring ni base de datos.
 *
 * Cubre autoría, alcance por sección, visibilidad de familias, año cerrado,
 * baja lógica y límite de contenido.
 */
@ExtendWith(MockitoExtension.class)
class ComunicadoServiceTest {

    @Mock private ComunicadoRepository comunicadoRepository;
    @Mock private UserRepository userRepository;
    @Mock private AnioEscolarRepository anioRepository;
    @Mock private SeccionRepository seccionRepository;
    @Mock private CursoRepository cursoRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private SecretariaRepository secretariaRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private ApoderadoRepository apoderadoRepository;
    @Mock private ApoderadoEstudianteRepository vinculoRepository;
    @Mock private MatriculaService matriculaService;
    @Mock private NotificacionService notificacionService;

    private ComunicadoService servicio;

    private User userAdmin;
    private User userDocente;
    private Docente docente;
    private User userDocenteAjeno;
    private Docente docenteAjeno;
    private User userSecretaria;
    private Secretaria secretaria;
    private User userEstudiante;
    private Estudiante estudiante;
    private User userApoderado;
    private Apoderado apoderado;
    private AnioEscolar anio;
    private AnioEscolar otroAnio;
    private Seccion seccionA;
    private Seccion seccionB;
    private Seccion seccionOtroAnio;

    @BeforeEach
    void setUp() {
        servicio = new ComunicadoService(comunicadoRepository, userRepository,
                anioRepository, seccionRepository, cursoRepository, docenteRepository,
                secretariaRepository, estudianteRepository, apoderadoRepository,
                vinculoRepository, matriculaService, notificacionService);

        anio = new AnioEscolar(2026, "Año Escolar 2026", LocalDate.of(2026, 3, 2));
        anio.setId(10L);
        otroAnio = new AnioEscolar(2027, "Año Escolar 2027", LocalDate.of(2027, 3, 1));
        otroAnio.setId(11L);

        userAdmin = user(1L, "admin@educa360.pe", User.Role.ADMIN, "Ana", "Ruiz");
        userDocente = user(2L, "dana@educa360.pe", User.Role.DOCENTE, "Dana", "Perez");
        docente = new Docente(userDocente, "Licenciada");
        docente.setId(20L);
        userDocenteAjeno = user(3L, "otto@educa360.pe", User.Role.DOCENTE, "Otto", "Diaz");
        docenteAjeno = new Docente(userDocenteAjeno, "Licenciado");
        docenteAjeno.setId(21L);
        userSecretaria = user(4L, "sofia@educa360.pe", User.Role.SECRETARIA, "Sofia", "Quintos");
        secretaria = new Secretaria(userSecretaria);
        secretaria.setId(30L);
        userEstudiante = user(5L, "alma@educa360.pe", User.Role.ESTUDIANTE, "Alma", "Luna");
        estudiante = new Estudiante(userEstudiante, LocalDate.of(2011, 3, 3));
        estudiante.setId(40L);
        userApoderado = user(6L, "lucia@educa360.pe", User.Role.APODERADO, "Lucia", "Soto");
        apoderado = new Apoderado(userApoderado);
        apoderado.setId(50L);

        seccionA = new Seccion(3, "A", "A-101", null, anio);
        seccionA.setId(60L);
        seccionB = new Seccion(3, "B", "B-101", null, anio);
        seccionB.setId(61L);
        seccionOtroAnio = new Seccion(3, "A", "A-101", null, otroAnio);
        seccionOtroAnio.setId(62L);
    }

    // ==================== ADMIN ====================

    @Test
    @DisplayName("ADMIN publica un comunicado general para todo el año")
    void adminCreaComunicadoGeneral() {
        prepararAutor(userAdmin);
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        when(comunicadoRepository.save(any(Comunicado.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ComunicadoResponse respuesta = servicio.crearAdmin("admin@educa360.pe",
                new ComunicadoCreateRequest(10L, null, "  Bienvenidos al año  "));

        assertNull(respuesta.seccionId());
        assertNull(respuesta.seccionEtiqueta());
        assertEquals("Bienvenidos al año", respuesta.contenido());
        assertEquals(1L, respuesta.autorId());
        assertTrue(respuesta.activo());

        ArgumentCaptor<Comunicado> captor = ArgumentCaptor.forClass(Comunicado.class);
        verify(comunicadoRepository).save(captor.capture());
        assertTrue(captor.getValue().esGeneral());
        assertEquals(anio, captor.getValue().getAnio());
    }

    @Test
    @DisplayName("ADMIN publica para una sección del mismo año")
    void adminCreaComunicadoDeSeccion() {
        prepararAutor(userAdmin);
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        when(seccionRepository.findById(60L)).thenReturn(Optional.of(seccionA));
        when(comunicadoRepository.save(any(Comunicado.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ComunicadoResponse respuesta = servicio.crearAdmin("admin@educa360.pe",
                new ComunicadoCreateRequest(10L, 60L, "Solo 3°A"));

        assertEquals(60L, respuesta.seccionId());
        assertEquals("3°A", respuesta.seccionEtiqueta());
        assertFalse(respuesta.contenido().isBlank());
    }

    @Test
    @DisplayName("ADMIN no puede asociar una sección de otro año")
    void adminRechazaSeccionDeOtroAnio() {
        prepararAutor(userAdmin);
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        when(seccionRepository.findById(62L)).thenReturn(Optional.of(seccionOtroAnio));

        assertThrows(BadRequestException.class, () -> servicio.crearAdmin(
                "admin@educa360.pe",
                new ComunicadoCreateRequest(10L, 62L, "No corresponde")));
        verify(comunicadoRepository, never()).save(any(Comunicado.class));
    }

    @Test
    @DisplayName("Un año CERRADO es solo lectura para comunicados")
    void anioCerradoEsSoloLectura() {
        anio.setEstado(AnioEscolar.Estado.CERRADO);
        prepararAutor(userAdmin);
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));

        assertThrows(ConflictException.class, () -> servicio.crearAdmin(
                "admin@educa360.pe",
                new ComunicadoCreateRequest(10L, null, "Tarde")));

        Comunicado existente = comunicado(70L, userAdmin, anio, null, "Viejo");
        when(comunicadoRepository.findById(70L)).thenReturn(Optional.of(existente));
        assertThrows(ConflictException.class, () -> servicio.actualizarAdmin(
                "admin@educa360.pe", 70L,
                new ComunicadoUpdateRequest(null, "Editado")));
        assertThrows(ConflictException.class, () -> servicio.eliminarAdmin(
                "admin@educa360.pe", 70L));
        assertTrue(existente.isActivo());
    }

    // ==================== SECRETARÍA ====================

    @Test
    @DisplayName("SECRETARÍA publica solo comunicados generales")
    void secretariaSoloPublicaGenerales() {
        when(userRepository.findByEmail("sofia@educa360.pe"))
                .thenReturn(Optional.of(userSecretaria));
        when(secretariaRepository.findByUser(userSecretaria))
                .thenReturn(Optional.of(secretaria));

        assertThrows(BadRequestException.class, () -> servicio.crearSecretaria(
                "sofia@educa360.pe",
                new ComunicadoCreateRequest(10L, 60L, "Solo una sección")));

        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        when(comunicadoRepository.save(any(Comunicado.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ComunicadoResponse respuesta = servicio.crearSecretaria("sofia@educa360.pe",
                new ComunicadoCreateRequest(10L, null, "Aviso general"));

        assertNull(respuesta.seccionId());
        assertEquals(4L, respuesta.autorId());
    }

    @Test
    @DisplayName("SECRETARÍA no edita ni elimina un comunicado de otro autor")
    void secretariaNoGestionaComunicadosAjenos() {
        when(userRepository.findByEmail("sofia@educa360.pe"))
                .thenReturn(Optional.of(userSecretaria));
        when(secretariaRepository.findByUser(userSecretaria))
                .thenReturn(Optional.of(secretaria));

        Comunicado ajeno = comunicado(70L, userAdmin, anio, null, "De admin");
        when(comunicadoRepository.findById(70L)).thenReturn(Optional.of(ajeno));

        assertThrows(NotFoundException.class, () -> servicio.actualizarSecretaria(
                "sofia@educa360.pe", 70L,
                new ComunicadoUpdateRequest(null, "Intento")));
        assertThrows(NotFoundException.class, () -> servicio.eliminarSecretaria(
                "sofia@educa360.pe", 70L));
        assertTrue(ajeno.isActivo());
    }

    // ==================== DOCENTE ====================

    @Test
    @DisplayName("DOCENTE publica en una sección donde dicta clase")
    void docentePublicaEnSeccionDondeDicta() {
        prepararAutor(userDocente);
        when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        when(seccionRepository.findById(60L)).thenReturn(Optional.of(seccionA));
        when(cursoRepository.findByDocenteId(20L))
                .thenReturn(List.of(curso(docente, seccionA)));
        when(comunicadoRepository.save(any(Comunicado.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ComunicadoResponse respuesta = servicio.crearDocente("dana@educa360.pe",
                new ComunicadoCreateRequest(10L, 60L, "Para mi clase"));

        assertEquals(60L, respuesta.seccionId());
        assertEquals(2L, respuesta.autorId());
    }

    @Test
    @DisplayName("DOCENTE publica en una sección donde es tutor")
    void docentePublicaEnSeccionDondeEsTutor() {
        prepararAutor(userDocente);
        seccionB.setTutor(docente);
        when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        when(seccionRepository.findById(61L)).thenReturn(Optional.of(seccionB));
        when(cursoRepository.findByDocenteId(20L)).thenReturn(List.of());
        when(comunicadoRepository.save(any(Comunicado.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ComunicadoResponse respuesta = servicio.crearDocente("dana@educa360.pe",
                new ComunicadoCreateRequest(10L, 61L, "Como tutora"));

        assertEquals(61L, respuesta.seccionId());
    }

    @Test
    @DisplayName("DOCENTE no publica en una sección ajena")
    void docenteNoPublicaEnSeccionAjena() {
        prepararAutor(userDocente);
        when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        when(seccionRepository.findById(61L)).thenReturn(Optional.of(seccionB));
        when(cursoRepository.findByDocenteId(20L))
                .thenReturn(List.of(curso(docente, seccionA)));

        assertThrows(NotFoundException.class, () -> servicio.crearDocente(
                "dana@educa360.pe",
                new ComunicadoCreateRequest(10L, 61L, "No es mía")));
        verify(comunicadoRepository, never()).save(any(Comunicado.class));
    }

    @Test
    @DisplayName("DOCENTE debe indicar una sección")
    void docenteDebeIndicarSeccion() {
        when(userRepository.findByEmail("dana@educa360.pe"))
                .thenReturn(Optional.of(userDocente));
        when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));

        assertThrows(BadRequestException.class, () -> servicio.crearDocente(
                "dana@educa360.pe",
                new ComunicadoCreateRequest(10L, null, "Sin sección")));
    }

    @Test
    @DisplayName("DOCENTE no elimina un comunicado de otro autor")
    void docenteNoEliminaComunicadoAjeno() {
        when(userRepository.findByEmail("dana@educa360.pe"))
                .thenReturn(Optional.of(userDocente));
        when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));

        Comunicado ajeno = comunicado(70L, userDocenteAjeno, anio, seccionA, "De Otto");
        when(comunicadoRepository.findById(70L)).thenReturn(Optional.of(ajeno));

        assertThrows(NotFoundException.class, () -> servicio.eliminarDocente(
                "dana@educa360.pe", 70L));
        assertTrue(ajeno.isActivo());
    }

    @Test
    @DisplayName("DOCENTE no convierte un comunicado de sección en general")
    void docenteNoConvierteComunicadoEnGeneral() {
        prepararAutor(userDocente);
        when(docenteRepository.findByUser(userDocente)).thenReturn(Optional.of(docente));
        when(cursoRepository.findByDocenteId(20L))
                .thenReturn(List.of(curso(docente, seccionA)));
        Comunicado propio = comunicado(70L, userDocente, anio, seccionA, "Original");
        when(comunicadoRepository.findById(70L)).thenReturn(Optional.of(propio));
        when(comunicadoRepository.save(any(Comunicado.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ComunicadoResponse respuesta = servicio.actualizarDocente(
                "dana@educa360.pe", 70L,
                new ComunicadoUpdateRequest(null, "Editado"));

        assertEquals(60L, respuesta.seccionId(),
                "sin sección explícita se conserva la sección original");
        assertEquals("Editado", respuesta.contenido());
    }

    // ==================== ESTUDIANTE Y APODERADO ====================

    @Test
    @DisplayName("El estudiante ve generales y los de su sección")
    void estudianteVeGeneralesYSuSeccion() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userEstudiante));
        when(estudianteRepository.findByUser(userEstudiante))
                .thenReturn(Optional.of(estudiante));
        Matricula matricula = new Matricula(estudiante, anio, seccionA,
                LocalDate.of(2026, 2, 20));
        when(matriculaService.vigenteDe(40L)).thenReturn(Optional.of(matricula));

        Comunicado general = comunicado(70L, userAdmin, anio, null, "General");
        Comunicado deSeccion = comunicado(71L, userDocente, anio, seccionA, "Sección A");
        when(comunicadoRepository.findVisibles(List.of(10L), List.of(60L)))
                .thenReturn(List.of(deSeccion, general));

        List<ComunicadoResponse> respuesta = servicio.listarEstudiante("alma@educa360.pe");

        assertEquals(2, respuesta.size());
        assertEquals(71L, respuesta.get(0).id());
        assertEquals(70L, respuesta.get(1).id());
    }

    @Test
    @DisplayName("El estudiante sin matrícula vigente no tiene comunicados")
    void estudianteSinMatriculaNoTieneComunicados() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userEstudiante));
        when(estudianteRepository.findByUser(userEstudiante))
                .thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(40L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> servicio.listarEstudiante("alma@educa360.pe"));
        verify(comunicadoRepository, never()).findVisibles(anyCollection(), anyCollection());
    }

    @Test
    @DisplayName("El apoderado ve generales y las secciones de sus hijos activos")
    void apoderadoVeSeccionesDeSusHijos() {
        when(userRepository.findByEmail("lucia@educa360.pe"))
                .thenReturn(Optional.of(userApoderado));
        when(apoderadoRepository.findByUser(userApoderado)).thenReturn(Optional.of(apoderado));

        Estudiante hijo = estudiante;
        ApoderadoEstudiante vinculo = new ApoderadoEstudiante(apoderado, hijo,
                ApoderadoEstudiante.Parentesco.MADRE, true);
        when(vinculoRepository.findByApoderadoIdAndActivoTrue(50L))
                .thenReturn(List.of(vinculo));
        Matricula matricula = new Matricula(hijo, anio, seccionA,
                LocalDate.of(2026, 2, 20));
        when(matriculaService.vigenteDe(40L)).thenReturn(Optional.of(matricula));

        Comunicado general = comunicado(70L, userAdmin, anio, null, "General");
        when(comunicadoRepository.findVisibles(Set.of(10L), Set.of(60L)))
                .thenReturn(List.of(general));

        List<ComunicadoResponse> respuesta = servicio.listarApoderado("lucia@educa360.pe");

        assertEquals(1, respuesta.size());
        assertEquals(70L, respuesta.get(0).id());
    }

    @Test
    @DisplayName("El apoderado sin hijos activos ve una lista vacía")
    void apoderadoSinHijosVeListaVacia() {
        when(userRepository.findByEmail("lucia@educa360.pe"))
                .thenReturn(Optional.of(userApoderado));
        when(apoderadoRepository.findByUser(userApoderado)).thenReturn(Optional.of(apoderado));
        when(vinculoRepository.findByApoderadoIdAndActivoTrue(50L)).thenReturn(List.of());

        List<ComunicadoResponse> respuesta = servicio.listarApoderado("lucia@educa360.pe");

        assertTrue(respuesta.isEmpty());
        verify(comunicadoRepository, never()).findVisibles(anyCollection(), anyCollection());
    }

    @Test
    @DisplayName("El apoderado ignora hijos deshabilitados o sin matrícula vigente")
    void apoderadoIgnoraHijosNoVigentes() {
        when(userRepository.findByEmail("lucia@educa360.pe"))
                .thenReturn(Optional.of(userApoderado));
        when(apoderadoRepository.findByUser(userApoderado)).thenReturn(Optional.of(apoderado));

        Estudiante retirado = new Estudiante(
                user(7L, "beto@educa360.pe", User.Role.ESTUDIANTE, "Beto", "Luna"),
                LocalDate.of(2011, 5, 5));
        retirado.setId(41L);
        retirado.setEstado(Estudiante.EstadoEstudiante.RETIRADO);
        ApoderadoEstudiante vinculo = new ApoderadoEstudiante(apoderado, retirado,
                ApoderadoEstudiante.Parentesco.PADRE, true);
        when(vinculoRepository.findByApoderadoIdAndActivoTrue(50L))
                .thenReturn(List.of(vinculo));

        List<ComunicadoResponse> respuesta = servicio.listarApoderado("lucia@educa360.pe");

        assertTrue(respuesta.isEmpty());
        verify(comunicadoRepository, never()).findVisibles(anyCollection(), anyCollection());
    }

    // ==================== BAJA Y CONTENIDO ====================

    @Test
    @DisplayName("ADMIN elimina lógicamente: el comunicado queda inactivo")
    void adminEliminaLogicamente() {
        prepararAutor(userAdmin);
        Comunicado propio = comunicado(70L, userAdmin, anio, seccionA, "Mensaje");
        when(comunicadoRepository.findById(70L)).thenReturn(Optional.of(propio));
        when(comunicadoRepository.save(any(Comunicado.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        servicio.eliminarAdmin("admin@educa360.pe", 70L);

        assertFalse(propio.isActivo());
        verify(comunicadoRepository).save(propio);
    }

    @Test
    @DisplayName("Un comunicado inactivo ya no se puede editar ni eliminar")
    void comunicadoInactivoNoSeGestiona() {
        prepararAutor(userAdmin);
        Comunicado inactivo = comunicado(70L, userAdmin, anio, null, "Viejo");
        inactivo.setActivo(false);
        when(comunicadoRepository.findById(70L)).thenReturn(Optional.of(inactivo));

        assertThrows(NotFoundException.class, () -> servicio.eliminarAdmin(
                "admin@educa360.pe", 70L));
    }

    @Test
    @DisplayName("El contenido vacío o mayor a 5000 caracteres se rechaza")
    void contenidoInvalidoSeRechaza() {
        prepararAutor(userAdmin);
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));

        assertThrows(BadRequestException.class, () -> servicio.crearAdmin(
                "admin@educa360.pe",
                new ComunicadoCreateRequest(10L, null, "   ")));
        assertThrows(BadRequestException.class, () -> servicio.crearAdmin(
                "admin@educa360.pe",
                new ComunicadoCreateRequest(10L, null, "x".repeat(5001))));
        verify(comunicadoRepository, never()).save(any(Comunicado.class));
    }

    // ==================== Helpers ====================

    private void prepararAutor(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private User user(Long id, String email, User.Role rol, String nombres, String apellidos) {
        User user = new User(email, "Secreta1234", nombres, apellidos, "1000000" + id, rol);
        user.setId(id);
        return user;
    }

    private Curso curso(Docente docente, Seccion seccion) {
        Curso curso = new Curso(docente, new Materia("Matemática", null), seccion);
        curso.setId(80L + seccion.getId());
        return curso;
    }

    private Comunicado comunicado(Long id, User autor, AnioEscolar anio, Seccion seccion,
                                  String contenido) {
        Comunicado comunicado = new Comunicado(autor, anio, seccion, contenido);
        comunicado.setId(id);
        return comunicado;
    }
}
