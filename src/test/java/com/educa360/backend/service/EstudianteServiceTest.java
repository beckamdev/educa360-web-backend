package com.educa360.backend.service;

import com.educa360.backend.dto.EstudianteEstadoRequest;
import com.educa360.backend.dto.EstudianteResponse;
import com.educa360.backend.dto.EstudianteUpdateRequest;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.EstudianteRepository;
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
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link EstudianteService} sin Spring ni base de datos.
 *
 * Cubre la edición acotada de la ficha y la simetría retiro/reingreso entre
 * el estado del alumno y su matrícula vigente.
 */
@ExtendWith(MockitoExtension.class)
class EstudianteServiceTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private ApoderadoEstudianteRepository vinculoRepository;

    @Mock
    private MatriculaService matriculaService;

    private EstudianteService servicio;

    @BeforeEach
    void setUp() {
        servicio = new EstudianteService(usuarioService, estudianteRepository,
                vinculoRepository, matriculaService);
    }

    @Test
    @DisplayName("El reingreso reactiva la matrícula vigente RETIRADA")
    void reingresoReactivaMatricula() {
        Estudiante estudiante = estudiante(1L, Estudiante.EstadoEstudiante.RETIRADO);
        Matricula matricula = matricula(estudiante);
        matricula.setSituacionFinal(Matricula.SituacionFinal.RETIRADO);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(1L)).thenReturn(Optional.of(matricula));
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(1L)).thenReturn(List.of());

        EstudianteResponse respuesta = servicio.cambiarEstado(1L,
                new EstudianteEstadoRequest(Estudiante.EstadoEstudiante.ACTIVO));

        assertEquals(Estudiante.EstadoEstudiante.ACTIVO, estudiante.getEstado());
        assertEquals(Matricula.SituacionFinal.EN_CURSO, matricula.getSituacionFinal());
        assertEquals(Estudiante.EstadoEstudiante.ACTIVO, respuesta.estado());
    }

    @Test
    @DisplayName("El retiro del alumno marca RETIRADA su matrícula vigente")
    void retiroMarcaMatricula() {
        Estudiante estudiante = estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO);
        Matricula matricula = matricula(estudiante);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(1L)).thenReturn(Optional.of(matricula));
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(1L)).thenReturn(List.of());

        servicio.cambiarEstado(1L,
                new EstudianteEstadoRequest(Estudiante.EstadoEstudiante.RETIRADO));

        assertEquals(Estudiante.EstadoEstudiante.RETIRADO, estudiante.getEstado());
        assertEquals(Matricula.SituacionFinal.RETIRADO, matricula.getSituacionFinal());
    }

    @Test
    @DisplayName("La edición acotada cambia la fecha de nacimiento sin tocar el estado")
    void actualizarSoloFechaNacimiento() {
        Estudiante estudiante = estudiante(1L, Estudiante.EstadoEstudiante.ACTIVO);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(1L)).thenReturn(Optional.empty());
        when(vinculoRepository.findByEstudianteIdAndActivoTrue(1L)).thenReturn(List.of());

        EstudianteResponse respuesta = servicio.actualizar(1L,
                new EstudianteUpdateRequest(LocalDate.of(2010, 5, 5), null));

        assertEquals(LocalDate.of(2010, 5, 5), respuesta.fechaNacimiento());
        assertEquals(Estudiante.EstadoEstudiante.ACTIVO, respuesta.estado());
    }

    @Test
    @DisplayName("Un alumno inexistente responde 404")
    void alumnoInexistente() {
        when(estudianteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> servicio.buscar(99L));
    }

    // ==================== Helpers ====================

    private Estudiante estudiante(Long id, Estudiante.EstadoEstudiante estado) {
        User user = new User();
        user.setNombres("Alma");
        user.setApellidos("Luna");
        user.setDni("30000001");
        user.setEmail("alma@educa360.pe");
        user.setActivo(true);
        Estudiante estudiante = new Estudiante(user, LocalDate.of(2011, 3, 3));
        estudiante.setId(id);
        estudiante.setEstado(estado);
        return estudiante;
    }

    private Matricula matricula(Estudiante estudiante) {
        AnioEscolar anio = new AnioEscolar(2026, "Año Escolar 2026", LocalDate.of(2026, 3, 2));
        anio.setId(10L);
        Seccion seccion = new Seccion(3, "A", "A-101", null, anio);
        seccion.setId(2L);
        Matricula matricula = new Matricula(estudiante, anio, seccion, LocalDate.of(2026, 2, 20));
        matricula.setId(5L);
        return matricula;
    }
}
