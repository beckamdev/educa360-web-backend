package com.educa360.backend.service;

import com.educa360.backend.dto.AnioEscolarRequest;
import com.educa360.backend.dto.AnioEscolarResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.BimestreRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Años escolares: el ancla anual de la estructura académica.
 *
 * Reglas:
 * - sólo un año ABIERTO a la vez;
 * - la fecha de inicio debe pertenecer al año calendario;
 * - un año CERRADO es solo lectura;
 * - no se cierra un año con bimestres abiertos.
 */
@Service
public class AnioEscolarService {

    private final AnioEscolarRepository anioRepository;
    private final BimestreRepository bimestreRepository;

    public AnioEscolarService(AnioEscolarRepository anioRepository,
                              BimestreRepository bimestreRepository) {
        this.anioRepository = anioRepository;
        this.bimestreRepository = bimestreRepository;
    }

    /** Alta: nace PLANIFICADO (editable). */
    @Transactional
    public AnioEscolarResponse crear(AnioEscolarRequest request) {
        validarInicioEnElAnio(request.anio(), request.fechaInicio());
        if (anioRepository.existsByAnio(request.anio())) {
            throw new ConflictException("Ya existe el año " + request.anio());
        }
        if (anioRepository.existsByNombre(request.nombre())) {
            throw new ConflictException("Ya existe un año con el nombre '" + request.nombre() + "'");
        }

        AnioEscolar anio = new AnioEscolar(
                request.anio(), request.nombre(), request.fechaInicio());
        return AnioEscolarResponse.desde(anioRepository.save(anio));
    }

    @Transactional(readOnly = true)
    public List<AnioEscolarResponse> listar() {
        return anioRepository.findAll(Sort.by(Sort.Direction.DESC, "anio")).stream()
                .map(AnioEscolarResponse::desde)
                .toList();
    }

    @Transactional
    public AnioEscolarResponse actualizar(Long id, AnioEscolarRequest request) {
        AnioEscolar anio = buscar(id);
        if (anio.estaCerrado()) {
            throw new ConflictException("El año " + anio.getAnio() + " está CERRADO: es solo lectura");
        }
        validarInicioEnElAnio(request.anio(), request.fechaInicio());
        if (anioRepository.existsByAnioAndIdNot(request.anio(), id)) {
            throw new ConflictException("Ya existe el año " + request.anio());
        }
        if (anioRepository.existsByNombreAndIdNot(request.nombre(), id)) {
            throw new ConflictException("Ya existe un año con el nombre '" + request.nombre() + "'");
        }

        anio.setAnio(request.anio());
        anio.setNombre(request.nombre());
        anio.setFechaInicio(request.fechaInicio());
        return AnioEscolarResponse.desde(anioRepository.save(anio));
    }

    @Transactional
    public AnioEscolarResponse cambiarEstado(Long id, AnioEscolar.Estado estado) {
        AnioEscolar anio = buscar(id);

        if (estado == AnioEscolar.Estado.ABIERTO
                && anioRepository.existsByEstadoAndIdNot(AnioEscolar.Estado.ABIERTO, id)) {
            throw new ConflictException("Ya hay un año abierto; ciérralo antes de abrir otro "
                    + "(sólo puede haber uno a la vez)");
        }
        if (estado == AnioEscolar.Estado.CERRADO
                && bimestreRepository.existsByAnioIdAndEstado(id, Bimestre.Estado.ABIERTO)) {
            throw new ConflictException("No se puede cerrar el año con bimestres abiertos: ciérralos primero");
        }

        anio.setEstado(estado);
        return AnioEscolarResponse.desde(anioRepository.save(anio));
    }

    // Helpers

    @Transactional(readOnly = true)
    public AnioEscolar buscar(Long id) {
        return anioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("El año " + id + " no existe"));
    }

    private void validarInicioEnElAnio(Integer anio, LocalDate inicio) {
        if (inicio.getYear() != anio) {
            throw new BadRequestException(
                    "La fecha de inicio debe pertenecer al año " + anio);
        }
    }
}
