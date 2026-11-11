package com.educa360.backend.service;

import com.educa360.backend.dto.HorarioSeccionResponse;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.DiaSemana;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.HorarioBloque;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.NotFoundException;
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

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** Horario de una sección: orden cronológico y 404, sin Spring ni base de datos. */
@ExtendWith(MockitoExtension.class)
class CursoServiceTest {

    @Mock private CursoRepository cursoRepository;
    @Mock private HorarioBloqueRepository horarioRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private UserRepository userRepository;
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

    private HorarioBloque bloque(Long id, DiaSemana dia, int hora) {
        HorarioBloque bloque = new HorarioBloque(curso, dia,
                LocalTime.of(hora, 0), LocalTime.of(hora + 1, 0));
        bloque.setId(id);
        return bloque;
    }
}
