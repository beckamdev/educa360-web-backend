package com.educa360.backend.service;

import com.educa360.backend.dto.HorarioSeccionResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.HorarioBloque;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.AsistenciaRepository;
import com.educa360.backend.repository.CalificacionRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.HorarioBloqueRepository;
import com.educa360.backend.repository.MaterialClaseRepository;
import com.educa360.backend.repository.UnidadRepository;
import com.educa360.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** Horario por sección, del estudiante y del hijo del apoderado: sin Spring ni base de datos. */
@ExtendWith(MockitoExtension.class)
class CursoServiceTest {

    @Mock private CursoRepository cursoRepository;
    @Mock private HorarioBloqueRepository horarioRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApoderadoRepository apoderadoRepository;
    @Mock private ApoderadoEstudianteRepository vinculoRepository;
    @Mock private UnidadRepository unidadRepository;
    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private CalificacionRepository calificacionRepository;
    @Mock private MaterialClaseRepository materialClaseRepository;
    @Mock private MateriaService materiaService;
    @Mock private SeccionService seccionService;
    @Mock private MatriculaService matriculaService;

    @InjectMocks
    private CursoService servicio;

    private Seccion seccion;
    private Curso curso;

    @BeforeEach
    void setUp() {
        seccion = new Seccion();
        seccion.setId(1L);

        User user = new User("doc@educa360.pe", "x", "Dana", "Perez",
                "20000011", User.Role.DOCENTE);
        Docente docente = new Docente(user, "Lic. en Matematicas");
        docente.setId(7L);

        Materia materia = new Materia("Matematicas", null);
        materia.setId(3L);

        curso = new Curso(docente, materia, seccion);
        curso.setId(5L);
    }

    @Test
    @DisplayName("Ordena los bloques por día (LUNES→SÁBADO) y hora de inicio")
    void ordenaPorDiaYHora() {
        HorarioBloque lunes9 = bloque(2L, DiaSemana.LUNES, 9);
        HorarioBloque martes = bloque(3L, DiaSemana.MARTES, 8);
        HorarioBloque lunes8 = bloque(1L, DiaSemana.LUNES, 8);

        when(seccionService.buscar(1L)).thenReturn(seccion);
        when(horarioRepository.findByCursoSeccionId(1L))
                .thenReturn(List.of(lunes9, martes, lunes8));

        List<HorarioSeccionResponse> horario = servicio.listarHorarioDeSeccion(1L);

        assertEquals(List.of(1L, 2L, 3L),
                horario.stream().map(HorarioSeccionResponse::bloqueId).toList(),
                "lunes 08:00, lunes 09:00 y martes 08:00");
        assertEquals("Matematicas", horario.get(0).materiaNombre());
        assertEquals("Dana Perez", horario.get(0).docenteNombre());
        assertEquals(5L, horario.get(0).cursoId());
    }

    @Test
    @DisplayName("Una sección inexistente responde 404 (NotFoundException)")
    void seccionInexistente() {
        when(seccionService.buscar(99L))
                .thenThrow(new NotFoundException("La sección 99 no existe"));

        assertThrows(NotFoundException.class,
                () -> servicio.listarHorarioDeSeccion(99L));
    }

    @Test
    @DisplayName("El DOCENTE ve la semana completa de SUS cursos en una sola petición")
    void docenteVeSuSemanaCompleta() {
        User user = new User("doc@educa360.pe", "x", "Dana", "Perez",
                "20000011", User.Role.DOCENTE);
        Docente docente = new Docente(user, "Lic. en Matematicas");
        docente.setId(7L);

        when(userRepository.findByEmail("doc@educa360.pe")).thenReturn(Optional.of(user));
        when(docenteRepository.findByUser(user)).thenReturn(Optional.of(docente));
        when(horarioRepository.findByCursoDocenteId(7L))
                .thenReturn(List.of(bloque(2L, DiaSemana.MARTES, 9),
                        bloque(1L, DiaSemana.LUNES, 8)));

        List<HorarioSeccionResponse> horario = servicio.listarHorarioDelDocente("doc@educa360.pe");

        assertEquals(List.of(1L, 2L),
                horario.stream().map(HorarioSeccionResponse::bloqueId).toList(),
                "lunes 08:00 antes que martes 09:00");
        assertEquals("Matematicas", horario.get(0).materiaNombre());
    }

    @Test
    @DisplayName("El ESTUDIANTE ve el horario de SU sección vigente")
    void estudianteVeElHorarioDeSuSeccion() {
        User user = new User("alu@educa360.pe", "x", "Alma", "Luna",
                "30000001", User.Role.ESTUDIANTE);
        Estudiante alumno = new Estudiante(user, LocalDate.of(2011, 3, 3));
        alumno.setId(11L);

        when(userRepository.findByEmail("alu@educa360.pe")).thenReturn(Optional.of(user));
        when(estudianteRepository.findByUser(user)).thenReturn(Optional.of(alumno));
        when(matriculaService.vigenteDe(11L)).thenReturn(Optional.of(matriculaDe(alumno)));
        when(horarioRepository.findByCursoSeccionId(1L))
                .thenReturn(List.of(bloque(2L, DiaSemana.MARTES, 9),
                        bloque(1L, DiaSemana.LUNES, 8)));

        List<HorarioSeccionResponse> horario = servicio.listarHorarioDelEstudiante("alu@educa360.pe");

        assertEquals(List.of(1L, 2L),
                horario.stream().map(HorarioSeccionResponse::bloqueId).toList());
        assertEquals("Matematicas", horario.get(0).materiaNombre());
        assertEquals("Dana Perez", horario.get(0).docenteNombre());
    }

    @Test
    @DisplayName("El ESTUDIANTE sin matrícula vigente ve lista vacía (no 404)")
    void estudianteSinMatriculaVeListaVacia() {
        User user = new User("alu@educa360.pe", "x", "Alma", "Luna",
                "30000001", User.Role.ESTUDIANTE);
        Estudiante alumno = new Estudiante(user, LocalDate.of(2011, 3, 3));
        alumno.setId(11L);

        when(userRepository.findByEmail("alu@educa360.pe")).thenReturn(Optional.of(user));
        when(estudianteRepository.findByUser(user)).thenReturn(Optional.of(alumno));
        when(matriculaService.vigenteDe(11L)).thenReturn(Optional.empty());

        assertEquals(List.of(), servicio.listarHorarioDelEstudiante("alu@educa360.pe"));
    }

    @Test
    @DisplayName("El APODERADO ve el horario de su hijo vinculado")
    void apoderadoVeElHorarioDeSuHijo() {
        Estudiante alumno = alumno();
        Apoderado papa = papa();
        ApoderadoEstudiante vinculo = new ApoderadoEstudiante(
                papa, alumno, ApoderadoEstudiante.Parentesco.PADRE, true);

        when(userRepository.findByEmail("papa@educa360.pe")).thenReturn(Optional.of(papa.getUser()));
        when(apoderadoRepository.findByUser(papa.getUser())).thenReturn(Optional.of(papa));
        when(vinculoRepository.findByApoderadoIdAndEstudianteId(20L, 11L))
                .thenReturn(Optional.of(vinculo));
        when(matriculaService.vigenteDe(11L)).thenReturn(Optional.of(matriculaDe(alumno)));
        when(horarioRepository.findByCursoSeccionId(1L))
                .thenReturn(List.of(bloque(1L, DiaSemana.LUNES, 8)));

        List<HorarioSeccionResponse> horario =
                servicio.listarHorarioDelHijo("papa@educa360.pe", 11L);

        assertEquals(1, horario.size());
        assertEquals("Matematicas", horario.get(0).materiaNombre());
    }

    @Test
    @DisplayName("El APODERADO no ve el horario de un hijo NO vinculado (404)")
    void apoderadoNoVeElHorarioDeUnHijoAjeno() {
        Apoderado papa = papa();
        when(userRepository.findByEmail("papa@educa360.pe")).thenReturn(Optional.of(papa.getUser()));
        when(apoderadoRepository.findByUser(papa.getUser())).thenReturn(Optional.of(papa));
        when(vinculoRepository.findByApoderadoIdAndEstudianteId(20L, 99L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> servicio.listarHorarioDelHijo("papa@educa360.pe", 99L));
    }

    private Estudiante alumno() {
        User user = new User("alu@educa360.pe", "x", "Alma", "Luna",
                "30000001", User.Role.ESTUDIANTE);
        Estudiante alumno = new Estudiante(user, LocalDate.of(2011, 3, 3));
        alumno.setId(11L);
        return alumno;
    }

    private Apoderado papa() {
        User user = new User("papa@educa360.pe", "x", "Pablo", "Soto",
                "40000001", User.Role.APODERADO);
        Apoderado papa = new Apoderado(user);
        papa.setId(20L);
        return papa;
    }

    private Matricula matriculaDe(Estudiante alumno) {
        AnioEscolar anio = new AnioEscolar(2026, "Año Escolar 2026",
                LocalDate.of(2026, 3, 2));
        anio.setId(9L);
        return new Matricula(alumno, anio, seccion, LocalDate.of(2026, 2, 20));
    }

    private HorarioBloque bloque(Long id, DiaSemana dia, int hora) {
        HorarioBloque bloque = new HorarioBloque(curso, dia,
                LocalTime.of(hora, 0), LocalTime.of(hora + 1, 0));
        bloque.setId(id);
        return bloque;
    }
}
