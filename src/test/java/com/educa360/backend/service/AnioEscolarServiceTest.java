package com.educa360.backend.service;

import com.educa360.backend.dto.AnioEscolarRequest;
import com.educa360.backend.dto.AnioEscolarResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.BimestreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link AnioEscolarService} sin Spring ni base de datos.
 *
 * Cubre lo que los tests de API no pueden ejecutar localmente sin Docker:
 * unicidad, fecha de inicio dentro del año, un solo año abierto y año cerrado
 * como solo lectura.
 */
@ExtendWith(MockitoExtension.class)
class AnioEscolarServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);
    private static final String NOMBRE = "Año Escolar 2026";

    @Mock
    private AnioEscolarRepository anioRepository;

    @Mock
    private BimestreRepository bimestreRepository;

    private AnioEscolarService servicio;

    @BeforeEach
    void setUp() {
        servicio = new AnioEscolarService(anioRepository, bimestreRepository);
    }

    @Test
    @DisplayName("Un año nuevo nace PLANIFICADO")
    void anioNacePlanificado() {
        when(anioRepository.existsByAnio(2026)).thenReturn(false);
        when(anioRepository.existsByNombre(NOMBRE)).thenReturn(false);
        when(anioRepository.save(any(AnioEscolar.class))).thenAnswer(inv -> inv.getArgument(0));

        AnioEscolarResponse respuesta = servicio.crear(
                new AnioEscolarRequest(2026, NOMBRE, INICIO));

        assertEquals(AnioEscolar.Estado.PLANIFICADO, respuesta.estado());
        assertEquals(2026, respuesta.anio());
    }

    @Test
    @DisplayName("La fecha de inicio debe pertenecer al año calendario")
    void fechaInicioFueraDelAnio() {
        assertThrows(BadRequestException.class, () -> servicio.crear(
                new AnioEscolarRequest(2026, NOMBRE, LocalDate.of(2027, 3, 2))));
    }

    @Test
    @DisplayName("El número de año es único")
    void anioDuplicado() {
        when(anioRepository.existsByAnio(2026)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> servicio.crear(new AnioEscolarRequest(2026, "Otro", INICIO)));
    }

    @Test
    @DisplayName("El nombre del año es único")
    void nombreDuplicado() {
        when(anioRepository.existsByAnio(2026)).thenReturn(false);
        when(anioRepository.existsByNombre(NOMBRE)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> servicio.crear(new AnioEscolarRequest(2026, NOMBRE, INICIO)));
    }

    @Test
    @DisplayName("Sólo puede haber un año ABIERTO a la vez")
    void soloUnAnioAbierto() {
        AnioEscolar segundo = anio(2L, 2027, "Año Escolar 2027",
                LocalDate.of(2027, 3, 1));
        when(anioRepository.findById(2L)).thenReturn(Optional.of(segundo));
        when(anioRepository.existsByEstadoAndIdNot(AnioEscolar.Estado.ABIERTO, 2L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> servicio.cambiarEstado(2L, AnioEscolar.Estado.ABIERTO));
    }

    @Test
    @DisplayName("No se cierra un año con bimestres ABIERTOS")
    void noSeCierraConBimestreAbierto() {
        AnioEscolar anio = anio(1L, 2026, NOMBRE, INICIO);
        when(anioRepository.findById(1L)).thenReturn(Optional.of(anio));
        when(bimestreRepository.existsByAnioIdAndEstado(1L, Bimestre.Estado.ABIERTO))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> servicio.cambiarEstado(1L, AnioEscolar.Estado.CERRADO));
    }

    @Test
    @DisplayName("Un año CERRADO es solo lectura: no se edita")
    void anioCerradoNoSeEdita() {
        AnioEscolar cerrado = anio(1L, 2026, NOMBRE, INICIO);
        cerrado.setEstado(AnioEscolar.Estado.CERRADO);
        when(anioRepository.findById(1L)).thenReturn(Optional.of(cerrado));

        assertThrows(ConflictException.class, () -> servicio.actualizar(1L,
                new AnioEscolarRequest(2026, NOMBRE, INICIO)));
    }

    @Test
    @DisplayName("Abrir un año sin otro vigente y cerrarlo sin bimestres abiertos funciona")
    void abrirYCerrarAnio() {
        AnioEscolar anio = anio(1L, 2026, NOMBRE, INICIO);
        when(anioRepository.findById(1L)).thenReturn(Optional.of(anio));
        when(anioRepository.existsByEstadoAndIdNot(AnioEscolar.Estado.ABIERTO, 1L)).thenReturn(false);
        when(bimestreRepository.existsByAnioIdAndEstado(1L, Bimestre.Estado.ABIERTO))
                .thenReturn(false);
        when(anioRepository.save(any(AnioEscolar.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(AnioEscolar.Estado.ABIERTO,
                servicio.cambiarEstado(1L, AnioEscolar.Estado.ABIERTO).estado());
        assertEquals(AnioEscolar.Estado.CERRADO,
                servicio.cambiarEstado(1L, AnioEscolar.Estado.CERRADO).estado());
    }

    private AnioEscolar anio(Long id, int numero, String nombre, LocalDate inicio) {
        AnioEscolar anio = new AnioEscolar(numero, nombre, inicio);
        anio.setId(id);
        return anio;
    }
}
