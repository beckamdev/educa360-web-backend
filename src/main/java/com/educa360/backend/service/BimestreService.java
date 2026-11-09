package com.educa360.backend.service;

import com.educa360.backend.dto.BimestreRequest;
import com.educa360.backend.dto.BimestreResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.BimestreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Bimestres de un año escolar.
 *
 * Reglas:
 * - viven dentro del año y no se solapan entre sí;
 * - sólo un bimestre ABIERTO en todo el sistema;
 * - se abre sólo si el año está ABIERTO;
 * - un bimestre CERRADO es solo lectura.
 */
@Service
public class BimestreService {

    private final BimestreRepository bimestreRepository;
    private final AnioEscolarService anioService;

    public BimestreService(BimestreRepository bimestreRepository,
                           AnioEscolarService anioService) {
        this.bimestreRepository = bimestreRepository;
        this.anioService = anioService;
    }

    @Transactional
    public BimestreResponse crear(Long anioId, BimestreRequest request) {
        AnioEscolar anio = anioService.buscar(anioId);
        if (anio.estaCerrado()) {
            throw new ConflictException("El año " + anio.getAnio() + " está CERRADO: es solo lectura");
        }
        validar(request, anio, null);
        return BimestreResponse.desde(bimestreRepository.save(
                new Bimestre(anio, request.numero(), request.fechaInicio(), request.fechaFin())));
    }

    @Transactional(readOnly = true)
    public List<BimestreResponse> listar(Long anioId) {
        anioService.buscar(anioId); // 404 si el año no existe
        return bimestreRepository.findByAnioIdOrderByNumeroAsc(anioId).stream()
                .map(BimestreResponse::desde)
                .toList();
    }

    /**
     * Catálogo de bimestres de solo lectura para cualquier rol autenticado.
     *
     * Sin {@code anioId} devuelve todos (año más reciente primero); con
     * {@code anioId} reutiliza {@link #listar(Long)} y responde 404 si el año
     * no existe.
     *
     * Existe porque el docente necesita el id del bimestre para crear su
     * primera unidad y no puede descubrirlo desde sus propios endpoints.
     */
    @Transactional(readOnly = true)
    public List<BimestreResponse> catalogo(Long anioId) {
        if (anioId != null) {
            return listar(anioId);
        }
        return bimestreRepository.findAll().stream()
                .sorted(Comparator.comparing((Bimestre bimestre) -> bimestre.getAnio().getAnio())
                        .reversed()
                        .thenComparing(Bimestre::getNumero))
                .map(BimestreResponse::desde)
                .toList();
    }

    @Transactional
    public BimestreResponse actualizar(Long id, BimestreRequest request) {
        Bimestre bimestre = buscar(id);
        if (bimestre.estaCerrado()) {
            throw new ConflictException("El " + bimestre.getNombre() + " está CERRADO: es solo lectura");
        }
        if (bimestre.getAnio().estaCerrado()) {
            throw new ConflictException("El año " + bimestre.getAnio().getAnio() + " está CERRADO: es solo lectura");
        }
        validar(request, bimestre.getAnio(), id);

        bimestre.setNumero(request.numero());
        bimestre.setFechaInicio(request.fechaInicio());
        bimestre.setFechaFin(request.fechaFin());
        return BimestreResponse.desde(bimestreRepository.save(bimestre));
    }

    @Transactional
    public BimestreResponse cambiarEstado(Long id, Bimestre.Estado estado) {
        Bimestre bimestre = buscar(id);

        if (estado == Bimestre.Estado.ABIERTO) {
            if (!bimestre.getAnio().estaAbierto()) {
                throw new ConflictException("El año " + bimestre.getAnio().getAnio()
                        + " no está ABIERTO: ábrelo antes de abrir un bimestre");
            }
            if (bimestreRepository.existsByEstadoAndIdNot(Bimestre.Estado.ABIERTO, id)) {
                throw new ConflictException("Ya hay un bimestre abierto; ciérralo antes de abrir otro "
                        + "(sólo puede haber uno a la vez)");
            }
        }

        bimestre.setEstado(estado);
        return BimestreResponse.desde(bimestreRepository.save(bimestre));
    }

    // Helpers

    @Transactional(readOnly = true)
    public Bimestre buscar(Long id) {
        return bimestreRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("El bimestre " + id + " no existe"));
    }

    private void validar(BimestreRequest request, AnioEscolar anio, Long idExcluido) {
        if (request.fechaFin().isBefore(request.fechaInicio())) {
            throw new BadRequestException("La fecha de fin no puede ser anterior a la de inicio");
        }
        if (request.fechaInicio().isBefore(anio.getFechaInicio())
                || request.fechaInicio().getYear() != anio.getAnio()
                || request.fechaFin().getYear() != anio.getAnio()) {
            throw new BadRequestException("El bimestre debe estar dentro del año "
                    + anio.getAnio() + " (desde " + anio.getFechaInicio() + ")");
        }
        boolean numeroDuplicado = (idExcluido == null)
                ? bimestreRepository.existsByAnioIdAndNumero(anio.getId(), request.numero())
                : bimestreRepository.existsByAnioIdAndNumeroAndIdNot(anio.getId(), request.numero(), idExcluido);
        if (numeroDuplicado) {
            throw new ConflictException("El año " + anio.getAnio()
                    + " ya tiene el Bimestre " + request.numero());
        }
        boolean solapa = bimestreRepository.findByAnioIdOrderByNumeroAsc(anio.getId()).stream()
                .filter(otro -> idExcluido == null || !otro.getId().equals(idExcluido))
                .anyMatch(otro -> !request.fechaFin().isBefore(otro.getFechaInicio())
                        && !otro.getFechaFin().isBefore(request.fechaInicio()));
        if (solapa) {
            throw new ConflictException("El bimestre se solapa con otro del año " + anio.getAnio());
        }
    }
}
