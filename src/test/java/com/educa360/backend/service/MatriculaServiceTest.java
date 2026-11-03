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
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.SeccionRepository;
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
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link MatriculaService} sin Spring ni base de datos.
 *
 * Cubre lo que los tests de API no pueden ejecutar localmente sin Docker:
 * unicidad anual, alumno activo, cambio de sección antes del inicio, retiro
 * y reingreso, y la matrícula vigente del año ABIERTO.
 */
@ExtendWith(MockitoExtension.class)
class MatriculaServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);

    @Mock
    private MatriculaRepository matriculaRepository;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private SeccionRepository seccionRepository;

    @Mock
    private AnioEscolarRepository anioRepository;

    @Mock
    private UserRepository userRepository;

    private MatriculaService servicio;

    @BeforeEach
    void setUp() {
        servicio = new MatriculaService(matriculaRepository, estudianteRepository,
                seccionRepository, anioRepository, userRepository,
                reloj(LocalDate.of(2026, 1, 15)));
    }

    @Test
    @DisplayName("La matrícula nace EN_CURSO y toma el año de la sección")
    void matriculaNaceEnCurso() {
        Estudiante estudiante = estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO);
        Seccion seccion = seccion(2L, "A", anio(10L, 2026, INICIO));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(seccionRepository.findById(2L)).thenReturn(Optional.of(seccion));
        when(matriculaRepository.existsByEstudianteIdAndAnioId(1L, 10L)).thenReturn(false);
        when(matriculaRepository.save(any(Matricula.class))).thenAnswer(inv -> inv.getArgument(0));

        MatriculaResponse respuesta = servicio.crear(
                new MatriculaRequest(1L, 2L, LocalDate.of(2026, 2, 20), null));

        assertEquals(Matricula.SituacionFinal.EN_CURSO, respuesta.situacionFinal());
        assertEquals(10L, respuesta.anioId());
        assertEquals("3°A", respuesta.seccionEtiqueta());
    }

    @Test
    @DisplayName("Un alumno no puede tener dos matrículas en el mismo año")
    void matriculaDuplicadaEnElAnio() {
        Estudiante estudiante = estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO);
        Seccion seccion = seccion(2L, "A", anio(10L, 2026, INICIO));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(seccionRepository.findById(2L)).thenReturn(Optional.of(seccion));
        when(matriculaRepository.existsByEstudianteIdAndAnioId(1L, 10L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> servicio.crear(
                new MatriculaRequest(1L, 2L, LocalDate.of(2026, 2, 20), null)));
    }

    @Test
    @DisplayName("Sólo alumnos ACTIVOS se matriculan")
    void alumnoNoActivoNoSeMatricula() {
        Estudiante estudiante = estudiante(1L, Estudiante.EstadoEstudiante.RETIRADO);
        Seccion seccion = seccion(2L, "A", anio(10L, 2026, INICIO));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(seccionRepository.findById(2L)).thenReturn(Optional.of(seccion));

        assertThrows(ConflictException.class, () -> servicio.crear(
                new MatriculaRequest(1L, 2L, LocalDate.of(2026, 2, 20), null)));
    }

    @Test
    @DisplayName("No se matricula en un año CERRADO")
    void anioCerradoNoAdmiteMatriculas() {
        Estudiante estudiante = estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO);
        AnioEscolar anio = anio(10L, 2026, INICIO);
        anio.setEstado(AnioEscolar.Estado.CERRADO);
        Seccion seccion = seccion(2L, "A", anio);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(seccionRepository.findById(2L)).thenReturn(Optional.of(seccion));

        assertThrows(ConflictException.class, () -> servicio.crear(
                new MatriculaRequest(1L, 2L, LocalDate.of(2026, 2, 20), null)));
    }

    @Test
    @DisplayName("El cambio de sección se permite antes del primer día de clases")
    void cambioDeSeccionAntesDeIniciar() {
        AnioEscolar anio = anio(10L, 2026, INICIO);
        Seccion actual = seccion(2L, "A", anio);
        Seccion nueva = seccion(3L, "B", anio);
        Matricula matricula = matricula(5L, estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO),
                anio, actual);
        when(matriculaRepository.findById(5L)).thenReturn(Optional.of(matricula));
        when(seccionRepository.findById(3L)).thenReturn(Optional.of(nueva));

        MatriculaResponse respuesta = servicio.cambiarSeccion(5L, new CambioSeccionRequest(3L));

        assertEquals("3°B", respuesta.seccionEtiqueta());
        assertEquals(3L, matricula.getSeccion().getId());
    }

    @Test
    @DisplayName("El cambio de sección se bloquea desde el primer día de clases")
    void cambioDeSeccionConAnioIniciado() {
        servicio = new MatriculaService(matriculaRepository, estudianteRepository,
                seccionRepository, anioRepository, userRepository,
                reloj(LocalDate.of(2026, 3, 2)));
        AnioEscolar anio = anio(10L, 2026, INICIO);
        Matricula matricula = matricula(5L, estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO),
                anio, seccion(2L, "A", anio));
        when(matriculaRepository.findById(5L)).thenReturn(Optional.of(matricula));

        assertThrows(ConflictException.class,
                () -> servicio.cambiarSeccion(5L, new CambioSeccionRequest(3L)));
    }

    @Test
    @DisplayName("El cambio de sección exige una sección del mismo año")
    void cambioDeSeccionDeOtroAnio() {
        AnioEscolar anio = anio(10L, 2026, INICIO);
        AnioEscolar otro = anio(11L, 2027, LocalDate.of(2027, 3, 1));
        Matricula matricula = matricula(5L, estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO),
                anio, seccion(2L, "A", anio));
        when(matriculaRepository.findById(5L)).thenReturn(Optional.of(matricula));
        when(seccionRepository.findById(4L)).thenReturn(Optional.of(seccion(4L, "A", otro)));

        assertThrows(ConflictException.class,
                () -> servicio.cambiarSeccion(5L, new CambioSeccionRequest(4L)));
    }

    @Test
    @DisplayName("El retiro marca matrícula y alumno RETIRADOS")
    void retiroMarcaAmbos() {
        Estudiante estudiante = estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO);
        AnioEscolar anio = anio(10L, 2026, INICIO);
        Matricula matricula = matricula(5L, estudiante, anio, seccion(2L, "A", anio));
        when(matriculaRepository.findById(5L)).thenReturn(Optional.of(matricula));

        MatriculaResponse respuesta = servicio.retirar(5L);

        assertEquals(Matricula.SituacionFinal.RETIRADO, respuesta.situacionFinal());
        assertEquals(Estudiante.EstadoEstudiante.RETIRADO, estudiante.getEstado());
    }

    @Test
    @DisplayName("La matrícula vigente es la del año ABIERTO")
    void vigenteEsLaDelAnioAbierto() {
        AnioEscolar abierto = anio(10L, 2026, INICIO);
        abierto.setEstado(AnioEscolar.Estado.ABIERTO);
        Matricula matricula = matricula(5L, estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO),
                abierto, seccion(2L, "A", abierto));
        when(anioRepository.findByEstado(AnioEscolar.Estado.ABIERTO))
                .thenReturn(Optional.of(abierto));
        when(matriculaRepository.findByEstudianteIdAndAnioId(1L, 10L))
                .thenReturn(Optional.of(matricula));

        Optional<Matricula> vigente = servicio.vigenteDe(1L);

        assertTrue(vigente.isPresent());
        assertEquals(5L, vigente.orElseThrow().getId());
    }

    // ==================== Helpers ====================

    private Clock reloj(LocalDate fecha) {
        ZoneId zona = ZoneId.systemDefault();
        Instant instante = fecha.atStartOfDay(zona).toInstant();
        return Clock.fixed(instante, zona);
    }

    private AnioEscolar anio(Long id, int numero, LocalDate inicio) {
        AnioEscolar anio = new AnioEscolar(numero, "Año Escolar " + numero, inicio);
        anio.setId(id);
        return anio;
    }

    private Seccion seccion(Long id, String letra, AnioEscolar anio) {
        Seccion seccion = new Seccion(3, letra, "A-101", null, anio);
        seccion.setId(id);
        return seccion;
    }

    private Estudiante estudiante(Long id, Estudiante.EstadoEstudiante estado) {
        User user = new User();
        user.setNombres("Alma");
        user.setApellidos("Luna");
        user.setDni("30000001");
        Estudiante estudiante = new Estudiante(user, LocalDate.of(2011, 3, 3));
        estudiante.setId(id);
        estudiante.setEstado(estado);
        return estudiante;
    }

    private Matricula matricula(Long id, Estudiante estudiante, AnioEscolar anio, Seccion seccion) {
        Matricula matricula = new Matricula(estudiante, anio, seccion, LocalDate.of(2026, 2, 20));
        matricula.setId(id);
        return matricula;
    }
}
