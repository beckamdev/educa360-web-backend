package com.educa360.backend.service;

import com.educa360.backend.dto.BimestreRequest;
import com.educa360.backend.dto.BimestreResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.repository.BimestreRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Reglas de {@link BimestreService} sin Spring ni base de datos.
 *
 * Cubre: dentro del año, sin solapes, número único, sólo uno ABIERTO y
 * apertura condicionada a que el año esté ABIERTO.
 */
@ExtendWith(MockitoExtension.class)
class BimestreServiceTest {

    private static final LocalDate INICIO_ANIO = LocalDate.of(2026, 3, 2);
    private static final LocalDate FIN_ANIO = LocalDate.of(2026, 12, 18);

    @Mock
    private BimestreRepository bimestreRepository;

    @Mock
    private AnioEscolarService anioService;

    private BimestreService servicio;
    private AnioEscolar anio;

    @BeforeEach
    void setUp() {
        servicio = new BimestreService(bimestreRepository, anioService);
        anio = new AnioEscolar(2026, "Año Escolar 2026", INICIO_ANIO, FIN_ANIO);
        anio.setId(1L);
    }

    @Test
    @DisplayName("Un bimestre nuevo nace PLANIFICADO y con nombre derivado")
    void bimestreNacePlanificado() {
        when(anioService.buscar(1L)).thenReturn(anio);
        when(bimestreRepository.existsByAnioIdAndNumero(1L, 1)).thenReturn(false);
        when(bimestreRepository.findByAnioIdOrderByNumeroAsc(1L)).thenReturn(List.of());
        when(bimestreRepository.save(any(Bimestre.class))).thenAnswer(inv -> inv.getArgument(0));

        BimestreResponse respuesta = servicio.crear(1L,
                new BimestreRequest(1, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 15)));

        assertEquals(Bimestre.Estado.PLANIFICADO, respuesta.estado());
        assertEquals("Bimestre I", respuesta.nombre());
    }

    @Test
    @DisplayName("El bimestre debe vivir dentro de las fechas del año")
    void bimestreFueraDelAnio() {
        when(anioService.buscar(1L)).thenReturn(anio);

        assertThrows(BadRequestException.class, () -> servicio.crear(1L,
                new BimestreRequest(1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30))));
    }

    @Test
    @DisplayName("El número de bimestre es único dentro del año")
    void numeroDeBimestreDuplicado() {
        when(anioService.buscar(1L)).thenReturn(anio);
        when(bimestreRepository.existsByAnioIdAndNumero(1L, 1)).thenReturn(true);

        assertThrows(ConflictException.class, () -> servicio.crear(1L,
                new BimestreRequest(1, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 10, 15))));
    }

    @Test
    @DisplayName("Dos bimestres del mismo año no pueden solaparse")
    void bimestresSolapados() {
        Bimestre primero = new Bimestre(anio, 1,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 15));
        primero.setId(10L);
        when(anioService.buscar(1L)).thenReturn(anio);
        when(bimestreRepository.existsByAnioIdAndNumero(1L, 2)).thenReturn(false);
        when(bimestreRepository.findByAnioIdOrderByNumeroAsc(1L)).thenReturn(List.of(primero));

        assertThrows(ConflictException.class, () -> servicio.crear(1L,
                new BimestreRequest(2, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 7, 31))));
    }

    @Test
    @DisplayName("Un bimestre no se abre si el año no está ABIERTO")
    void bimestreSoloSeAbreConAnioAbierto() {
        Bimestre bimestre = bimestre(10L, 1, Bimestre.Estado.PLANIFICADO);
        when(bimestreRepository.findById(10L)).thenReturn(Optional.of(bimestre));

        assertThrows(ConflictException.class,
                () -> servicio.cambiarEstado(10L, Bimestre.Estado.ABIERTO));
    }

    @Test
    @DisplayName("Sólo puede haber un bimestre ABIERTO a la vez")
    void soloUnBimestreAbierto() {
        anio.setEstado(AnioEscolar.Estado.ABIERTO);
        Bimestre bimestre = bimestre(10L, 1, Bimestre.Estado.PLANIFICADO);
        when(bimestreRepository.findById(10L)).thenReturn(Optional.of(bimestre));
        when(bimestreRepository.existsByEstadoAndIdNot(Bimestre.Estado.ABIERTO, 10L))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> servicio.cambiarEstado(10L, Bimestre.Estado.ABIERTO));
    }

    @Test
    @DisplayName("Un bimestre CERRADO es solo lectura: no se edita")
    void bimestreCerradoNoSeEdita() {
        Bimestre cerrado = bimestre(10L, 1, Bimestre.Estado.CERRADO);
        when(bimestreRepository.findById(10L)).thenReturn(Optional.of(cerrado));

        assertThrows(ConflictException.class, () -> servicio.actualizar(10L,
                new BimestreRequest(1, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 15))));
    }

    @Test
    @DisplayName("Abrir un bimestre con el año ABIERTO y sin otro vigente funciona")
    void abrirBimestre() {
        anio.setEstado(AnioEscolar.Estado.ABIERTO);
        Bimestre bimestre = bimestre(10L, 1, Bimestre.Estado.PLANIFICADO);
        when(bimestreRepository.findById(10L)).thenReturn(Optional.of(bimestre));
        when(bimestreRepository.existsByEstadoAndIdNot(Bimestre.Estado.ABIERTO, 10L))
                .thenReturn(false);
        when(bimestreRepository.save(any(Bimestre.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(Bimestre.Estado.ABIERTO,
                servicio.cambiarEstado(10L, Bimestre.Estado.ABIERTO).estado());
    }

    private Bimestre bimestre(Long id, int numero, Bimestre.Estado estado) {
        Bimestre bimestre = new Bimestre(anio, numero,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 15));
        bimestre.setId(id);
        bimestre.setEstado(estado);
        return bimestre;
    }
}
