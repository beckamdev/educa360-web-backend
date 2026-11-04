package com.educa360.backend.service;

import com.educa360.backend.dto.BoletaResponse;
import com.educa360.backend.dto.CierreAlumnoResponse;
import com.educa360.backend.dto.CierreResponse;
import com.educa360.backend.dto.HistorialEstudianteResponse;
import com.educa360.backend.dto.ReporteAsistenciaResponse;
import com.educa360.backend.dto.ReporteCalificacionesResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Calificacion;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.HorarioBloque;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.Unidad;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.AsistenciaRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.CalificacionRepository;
import com.educa360.backend.repository.CursoRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link CierreService} sin Spring ni base de datos.
 *
 * Cubre el bloqueo por bimestres/notas, la aprobación por curso y asistencia,
 * el retiro, la graduación de 5°, la idempotencia, boletas, historial y
 * reportes.
 */
@ExtendWith(MockitoExtension.class)
class CierreServiceTest {

    @Mock private AnioEscolarRepository anioRepository;
    @Mock private BimestreRepository bimestreRepository;
    @Mock private SeccionRepository seccionRepository;
    @Mock private MatriculaRepository matriculaRepository;
    @Mock private CursoRepository cursoRepository;
    @Mock private CalificacionRepository calificacionRepository;
    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private ApoderadoRepository apoderadoRepository;
    @Mock private ApoderadoEstudianteRepository vinculoRepository;
    @Mock private UserRepository userRepository;
    @Mock private MatriculaService matriculaService;

    private CierreService servicio;

    private User userEstudiante;
    private Estudiante estudiante;
    private User userApoderado;
    private Apoderado apoderado;
    private Docente docente;
    private AnioEscolar anio;
    private Bimestre bimestreI;
    private Bimestre bimestreII;
    private Seccion seccion;
    private Curso matematica;
    private Curso comunicacion;
    private Matricula matricula;

    @BeforeEach
    void setUp() {
        servicio = new CierreService(anioRepository, bimestreRepository,
                seccionRepository, matriculaRepository, cursoRepository,
                calificacionRepository, asistenciaRepository, estudianteRepository,
                apoderadoRepository, vinculoRepository, userRepository, matriculaService);

        anio = new AnioEscolar(2026, "Año Escolar 2026", LocalDate.of(2026, 3, 2));
        anio.setId(10L);
        anio.setEstado(AnioEscolar.Estado.ABIERTO);

        bimestreI = new Bimestre(anio, 1, LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 5, 15));
        bimestreI.setId(20L);
        bimestreI.setEstado(Bimestre.Estado.CERRADO);
        bimestreII = new Bimestre(anio, 2, LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 7, 24));
        bimestreII.setId(21L);
        bimestreII.setEstado(Bimestre.Estado.CERRADO);

        userEstudiante = user(1L, "alma@educa360.pe", User.Role.ESTUDIANTE,
                "Alma", "Luna");
        estudiante = new Estudiante(userEstudiante, LocalDate.of(2011, 3, 3));
        estudiante.setId(40L);

        userApoderado = user(2L, "lucia@educa360.pe", User.Role.APODERADO,
                "Lucia", "Soto");
        apoderado = new Apoderado(userApoderado);
        apoderado.setId(50L);

        User userDocente = user(3L, "dana@educa360.pe", User.Role.DOCENTE,
                "Dana", "Perez");
        docente = new Docente(userDocente, "Licenciada");
        docente.setId(30L);

        seccion = new Seccion(3, "A", "A-101", null, anio);
        seccion.setId(60L);
        matematica = new Curso(docente, new Materia("Matemática", null), seccion);
        matematica.setId(70L);
        comunicacion = new Curso(docente, new Materia("Comunicación", null), seccion);
        comunicacion.setId(71L);

        matricula = new Matricula(estudiante, anio, seccion, LocalDate.of(2026, 2, 20));
        matricula.setId(80L);
    }

    // ==================== Preview y cierre ====================

    @Test
    @DisplayName("El preview bloquea el cierre si hay bimestres abiertos")
    void previewBloqueaConBimestresAbiertos() {
        bimestreI.setEstado(Bimestre.Estado.ABIERTO);
        prepararCierre(List.of(), List.of());

        CierreResponse respuesta = servicio.preview(10L);

        assertFalse(respuesta.listo());
        assertEquals(2, respuesta.bimestres());
        assertEquals(1, respuesta.bimestresCerrados());
        assertTrue(respuesta.bloqueos().stream()
                .anyMatch(bloqueo -> bloqueo.contains("Bimestre I")
                        && bloqueo.contains("no está CERRADO")), respuesta.bloqueos().toString());
    }

    @Test
    @DisplayName("Aprueba si todos los cursos superan 11.00 y la asistencia llega a 70%")
    void apruebaConTodosLosCursosYAsistencia() {
        prepararCierre(
                List.of(nota(matematica, bimestreI, "Examen", "15.00"),
                        nota(comunicacion, bimestreI, "Examen", "16.00")),
                asistencias(7, 0, 0, 3));

        CierreResponse respuesta = servicio.preview(10L);

        assertTrue(respuesta.listo(), respuesta.bloqueos().toString());
        assertEquals(1, respuesta.aprobados());
        CierreAlumnoResponse alumno = respuesta.alumnos().get(0);
        assertEquals(Matricula.SituacionFinal.APROBADO, alumno.situacionPropuesta());
        assertEquals(new BigDecimal("15.50"), alumno.promedioGeneral());
        assertEquals(new BigDecimal("70.00"), alumno.asistenciaPorcentaje());
    }

    @Test
    @DisplayName("Un curso desaprobado impide APROBADO aunque el promedio general sea alto")
    void cursoDesaprobadoImpideAprobado() {
        prepararCierre(
                List.of(nota(matematica, bimestreI, "Examen", "10.00"),
                        nota(comunicacion, bimestreI, "Examen", "18.00")),
                asistencias(10, 0, 0, 0));

        CierreResponse respuesta = servicio.preview(10L);

        assertEquals(1, respuesta.desaprobados());
        CierreAlumnoResponse alumno = respuesta.alumnos().get(0);
        assertEquals(Matricula.SituacionFinal.DESAPROBADO, alumno.situacionPropuesta());
        assertEquals(new BigDecimal("14.00"), alumno.promedioGeneral());
        assertTrue(alumno.cursos().stream()
                .anyMatch(curso -> !curso.aprobado() && curso.tieneNotas()));
    }

    @Test
    @DisplayName("Una asistencia global menor a 70% desaprueba aunque las notas sean altas")
    void asistenciaBajaDesaprueba() {
        prepararCierre(
                List.of(nota(matematica, bimestreI, "Examen", "16.00"),
                        nota(comunicacion, bimestreI, "Examen", "16.00")),
                asistencias(6, 0, 0, 4));

        CierreResponse respuesta = servicio.preview(10L);

        assertEquals(1, respuesta.desaprobados());
        assertEquals(Matricula.SituacionFinal.DESAPROBADO,
                respuesta.alumnos().get(0).situacionPropuesta());
        assertEquals(new BigDecimal("60.00"),
                respuesta.alumnos().get(0).asistenciaPorcentaje());
    }

    @Test
    @DisplayName("Si falta el promedio anual de un curso, el cierre se bloquea")
    void notasFaltantesBloqueanElCierre() {
        prepararCierre(
                List.of(nota(matematica, bimestreI, "Examen", "16.00")),
                asistencias(10, 0, 0, 0));

        CierreResponse respuesta = servicio.preview(10L);

        assertFalse(respuesta.listo());
        assertEquals(1, respuesta.conNotasFaltantes());
        CierreAlumnoResponse alumno = respuesta.alumnos().get(0);
        assertTrue(alumno.faltanNotas());
        assertNull(alumno.situacionPropuesta());
        assertTrue(respuesta.bloqueos().stream()
                .anyMatch(bloqueo -> bloqueo.contains("Comunicación")), respuesta.bloqueos().toString());

        assertThrows(ConflictException.class, () -> servicio.cerrar(10L));
        verify(anioRepository, never()).save(any(AnioEscolar.class));
    }

    @Test
    @DisplayName("Una nota de unidad BORRADOR no cuenta para el cierre")
    void notaDeBorradorNoCuenta() {
        Calificacion borrador = nota(comunicacion, bimestreI, "Examen", "16.00");
        borrador.getUnidad().setEstado(Unidad.Estado.BORRADOR);
        prepararCierre(
                List.of(nota(matematica, bimestreI, "Examen", "16.00"), borrador),
                asistencias(10, 0, 0, 0));

        CierreResponse respuesta = servicio.preview(10L);

        assertFalse(respuesta.listo());
        assertTrue(respuesta.alumnos().get(0).faltanNotas());
    }

    @Test
    @DisplayName("Un alumno RETIRADO se conserva y no bloquea el cierre por notas")
    void retiradoSeConservaYNoBloquea() {
        matricula.setSituacionFinal(Matricula.SituacionFinal.RETIRADO);
        prepararCierre(List.of(), List.of());

        CierreResponse respuesta = servicio.preview(10L);

        assertTrue(respuesta.listo(), respuesta.bloqueos().toString());
        assertEquals(1, respuesta.retirados());
        CierreAlumnoResponse alumno = respuesta.alumnos().get(0);
        assertEquals(Matricula.SituacionFinal.RETIRADO, alumno.situacionPropuesta());
        assertFalse(alumno.faltanNotas());
    }

    @Test
    @DisplayName("El cierre aplica situaciones, egresa a 5° y deja el año CERRADO")
    void cierreAplicaSituacionesYEgresa() {
        seccion.setGrado(5);
        prepararCierre(
                List.of(nota(matematica, bimestreI, "Examen", "16.00"),
                        nota(comunicacion, bimestreI, "Examen", "17.00")),
                asistencias(10, 0, 0, 0));
        when(matriculaRepository.findById(80L)).thenReturn(Optional.of(matricula));
        when(matriculaRepository.save(any(Matricula.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(anioRepository.save(any(AnioEscolar.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CierreResponse respuesta = servicio.cerrar(10L);

        assertEquals(Matricula.SituacionFinal.APROBADO, matricula.getSituacionFinal());
        assertEquals(Estudiante.EstadoEstudiante.EGRESADO, estudiante.getEstado());
        assertEquals(AnioEscolar.Estado.CERRADO, anio.getEstado());
        assertEquals(1, respuesta.aprobados());
        verify(matriculaRepository).save(matricula);
        verify(anioRepository).save(anio);
    }

    @Test
    @DisplayName("Repetir el cierre de un año ya CERRADO no vuelve a aplicar efectos")
    void cierreEsIdempotente() {
        anio.setEstado(AnioEscolar.Estado.CERRADO);
        matricula.setSituacionFinal(Matricula.SituacionFinal.APROBADO);
        prepararCierre(
                List.of(nota(matematica, bimestreI, "Examen", "16.00"),
                        nota(comunicacion, bimestreI, "Examen", "16.00")),
                asistencias(10, 0, 0, 0));

        CierreResponse respuesta = servicio.cerrar(10L);

        assertTrue(respuesta.listo());
        verify(matriculaRepository, never()).save(any(Matricula.class));
        verify(anioRepository, never()).save(any(AnioEscolar.class));
    }

    // ==================== Boleta e historial ====================

    @Test
    @DisplayName("La boleta trae cursos, columnas de bimestre, promedio y asistencia")
    void boletaCalculaCursosYBimestres() {
        when(userRepository.findByEmail("alma@educa360.pe"))
                .thenReturn(Optional.of(userEstudiante));
        when(estudianteRepository.findByUser(userEstudiante))
                .thenReturn(Optional.of(estudiante));
        when(matriculaRepository.findByEstudianteIdAndAnioId(40L, 10L))
                .thenReturn(Optional.of(matricula));
        prepararCalculo(
                List.of(nota(matematica, bimestreI, "Examen", "14.00"),
                        nota(matematica, bimestreII, "Examen", "16.00"),
                        nota(comunicacion, bimestreI, "Examen", "18.00")),
                asistencias(9, 0, 0, 1));

        BoletaResponse boleta = servicio.boletaDelEstudiante("alma@educa360.pe", 10L);

        assertEquals(2, boleta.bimestres().size());
        assertEquals(2, boleta.cursos().size());
        // Los cursos se ordenan por nombre de materia: Comunicación antes que Matemática.
        assertEquals(new BigDecimal("18.00"), boleta.cursos().get(0).promedioAnual());
        assertEquals(new BigDecimal("15.00"), boleta.cursos().get(1).promedioAnual());
        assertEquals(new BigDecimal("16.50"), boleta.promedioGeneral());
        assertEquals(new BigDecimal("90.00"), boleta.asistenciaPorcentaje());
        assertEquals(Matricula.SituacionFinal.EN_CURSO, boleta.situacionFinal());
    }

    @Test
    @DisplayName("La boleta del apoderado rechaza un hijo inactivo")
    void boletaApoderadoRechazaHijoInactivo() {
        estudiante.setEstado(Estudiante.EstadoEstudiante.RETIRADO);
        when(userRepository.findByEmail("lucia@educa360.pe"))
                .thenReturn(Optional.of(userApoderado));
        when(apoderadoRepository.findByUser(userApoderado)).thenReturn(Optional.of(apoderado));
        ApoderadoEstudiante vinculo = new ApoderadoEstudiante(apoderado, estudiante,
                ApoderadoEstudiante.Parentesco.MADRE, true);
        when(vinculoRepository.findByApoderadoIdAndEstudianteId(50L, 40L))
                .thenReturn(Optional.of(vinculo));

        assertThrows(NotFoundException.class,
                () -> servicio.boletaDelHijo("lucia@educa360.pe", 40L, 10L));
    }

    @Test
    @DisplayName("El historial lista los años del alumno con su situación")
    void historialListaAnios() {
        when(estudianteRepository.findById(40L)).thenReturn(Optional.of(estudiante));
        when(matriculaRepository.findByEstudianteId(40L)).thenReturn(List.of(matricula));
        prepararCalculo(
                List.of(nota(matematica, bimestreI, "Examen", "16.00"),
                        nota(comunicacion, bimestreI, "Examen", "16.00")),
                asistencias(10, 0, 0, 0));

        HistorialEstudianteResponse historial = servicio.historial(40L);

        assertEquals(1, historial.anios().size());
        assertEquals(2026, historial.anios().get(0).anio());
        assertEquals("3°A", historial.anios().get(0).seccionEtiqueta());
        assertEquals(new BigDecimal("16.00"), historial.anios().get(0).promedioGeneral());
    }

    // ==================== Reportes ====================

    @Test
    @DisplayName("Los reportes exigen el año escolar")
    void reportesExigenAnio() {
        assertThrows(BadRequestException.class,
                () -> servicio.reporteAsistencia(null, null, null));
        assertThrows(BadRequestException.class,
                () -> servicio.reporteCalificaciones(null, null, null));
    }

    @Test
    @DisplayName("El reporte de calificaciones incluye cursos y promedio general")
    void reporteCalificacionesIncluyeCursos() {
        prepararCierre(
                List.of(nota(matematica, bimestreI, "Examen", "12.00"),
                        nota(comunicacion, bimestreI, "Examen", "16.00")),
                asistencias(10, 0, 0, 0));

        ReporteCalificacionesResponse reporte =
                servicio.reporteCalificaciones(10L, null, null);

        assertEquals(1, reporte.alumnos().size());
        assertEquals(2, reporte.alumnos().get(0).cursos().size());
        assertEquals(new BigDecimal("14.00"),
                reporte.alumnos().get(0).promedioGeneral());
    }

    @Test
    @DisplayName("El reporte de asistencia devuelve totales y porcentaje")
    void reporteAsistenciaDevuelveTotales() {
        prepararCierre(List.of(), asistencias(5, 2, 1, 2));

        ReporteAsistenciaResponse reporte = servicio.reporteAsistencia(10L, null, null);

        assertEquals(1, reporte.alumnos().size());
        assertEquals(10, reporte.alumnos().get(0).total());
        assertEquals(8, reporte.alumnos().get(0).efectivas());
        assertEquals(new BigDecimal("80.00"), reporte.alumnos().get(0).porcentaje());
    }

    // ==================== Helpers ====================

    private void prepararCierre(List<Calificacion> notas, List<Asistencia> asistencias) {
        when(anioRepository.findById(10L)).thenReturn(Optional.of(anio));
        prepararCalculo(notas, asistencias);
    }

    /**
     * Stubs compartidos por los tests de cálculo. Son {@code lenient} porque
     * cada caso usa un subconjunto distinto (por ejemplo, un reporte de
     * asistencia no consulta cursos ni notas).
     */
    private void prepararCalculo(List<Calificacion> notas, List<Asistencia> asistencias) {
        lenient().when(bimestreRepository.findByAnioIdOrderByNumeroAsc(10L))
                .thenReturn(List.of(bimestreI, bimestreII));
        lenient().when(matriculaRepository.findByAnioId(10L))
                .thenReturn(List.of(matricula));
        lenient().when(cursoRepository.findBySeccionId(60L))
                .thenReturn(List.of(matematica, comunicacion));
        lenient().when(calificacionRepository
                .findByMatriculaIdOrderByUnidadBimestreNumeroAscUnidadNumeroAscCriterioAsc(80L))
                .thenReturn(notas);
        lenient().when(asistenciaRepository
                .findByMatriculaIdOrderByFechaAscBloqueHoraInicioAsc(80L))
                .thenReturn(asistencias);
    }

    private Calificacion nota(Curso curso, Bimestre bimestre, String criterio, String valor) {
        Unidad unidad = new Unidad(curso, bimestre, 1, "Unidad 1");
        unidad.setId(90L + curso.getId());
        unidad.setEstado(Unidad.Estado.PUBLICADA);
        Calificacion calificacion = new Calificacion(matricula, unidad, criterio);
        calificacion.setNota(new BigDecimal(valor));
        return calificacion;
    }

    private List<Asistencia> asistencias(int presentes, int tardanzas, int justificadas,
                                         int faltas) {
        List<Asistencia> filas = new ArrayList<>();
        int dia = 1;
        for (int i = 0; i < presentes; i++) {
            filas.add(asistencia(dia++, Asistencia.Estado.PRESENTE));
        }
        for (int i = 0; i < tardanzas; i++) {
            filas.add(asistencia(dia++, Asistencia.Estado.TARDANZA));
        }
        for (int i = 0; i < justificadas; i++) {
            filas.add(asistencia(dia++, Asistencia.Estado.JUSTIFICADA));
        }
        for (int i = 0; i < faltas; i++) {
            filas.add(asistencia(dia++, Asistencia.Estado.FALTA));
        }
        return filas;
    }

    private Asistencia asistencia(int dia, Asistencia.Estado estado) {
        HorarioBloque bloque = new HorarioBloque(matematica, DiaSemana.LUNES,
                LocalTime.of(8, 0), LocalTime.of(9, 0));
        bloque.setId(100L);
        Asistencia asistencia = new Asistencia(matricula, bloque,
                LocalDate.of(2026, 3, Math.min(dia, 28)));
        asistencia.setEstado(estado);
        return asistencia;
    }

    private User user(Long id, String email, User.Role rol, String nombres, String apellidos) {
        User user = new User(email, "Secreta1234", nombres, apellidos, "1000000" + id, rol);
        user.setId(id);
        return user;
    }
}
