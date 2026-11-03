package com.educa360.backend.service;

import com.educa360.backend.dto.SeccionRequest;
import com.educa360.backend.dto.SeccionResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.SeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Secciones: los grupos de alumnos por año ("3°A").
 *
 * Sólo secundaria → el DTO acota grado 1..5, sin campo "nivel".
 * Regla: "grado+letra" es único DENTRO de un año. La sección no se mueve de
 * año: si se necesita en otro, se crea otra fila.
 */
@Service
public class SeccionService {

    private final SeccionRepository seccionRepository;
    private final AnioEscolarService anioService;
    private final DocenteRepository docenteRepository;

    public SeccionService(SeccionRepository seccionRepository,
                          AnioEscolarService anioService,
                          DocenteRepository docenteRepository) {
        this.seccionRepository = seccionRepository;
        this.anioService = anioService;
        this.docenteRepository = docenteRepository;
    }

    @Transactional
    public SeccionResponse crear(SeccionRequest request) {
        AnioEscolar anio = anioService.buscar(request.anioId());
        if (anio.estaCerrado()) {
            throw new ConflictException("El año " + anio.getAnio() + " está CERRADO: es solo lectura");
        }
        Docente tutor = buscarTutor(request.tutorId());
        if (seccionRepository.existsByGradoAndLetraAndAnio(request.grado(), request.letra(), anio)) {
            throw new ConflictException("Ya existe la sección " + request.grado() + "°" + request.letra()
                    + " en el año " + anio.getNombre());
        }
        Seccion seccion = new Seccion(request.grado(), request.letra(), request.aula(), tutor, anio);
        return SeccionResponse.desde(seccionRepository.save(seccion));
    }

    /** Listado completo, o filtrado por año (?anioId=) para el panel. */
    @Transactional(readOnly = true)
    public List<SeccionResponse> listar(Long anioId) {
        List<Seccion> secciones = anioId == null
                ? seccionRepository.findAll()
                : seccionRepository.findByAnioId(anioId);
        return secciones.stream().map(SeccionResponse::desde).toList();
    }

    /**
     * Edición del grupo: grado, letra, aula y tutor. NO mueve la sección de
     * año — cambiarla arrastraría matrículas, asistencia y notas.
     */
    @Transactional
    public SeccionResponse actualizar(Long id, SeccionRequest request) {
        Seccion seccion = buscar(id);
        AnioEscolar anio = seccion.getAnio();
        if (anio.estaCerrado()) {
            throw new ConflictException("El año " + anio.getAnio() + " está CERRADO: es solo lectura");
        }
        if (!anio.getId().equals(request.anioId())) {
            throw new ConflictException("La sección no se puede mover de año: crea una nueva en "
                    + request.anioId());
        }
        Docente tutor = buscarTutor(request.tutorId());
        boolean cambioDeNombre = !seccion.getGrado().equals(request.grado())
                || !seccion.getLetra().equals(request.letra());
        if (cambioDeNombre
                && seccionRepository.existsByGradoAndLetraAndAnio(request.grado(), request.letra(), anio)) {
            throw new ConflictException("Ya existe la sección " + request.grado() + "°" + request.letra()
                    + " en el año " + anio.getNombre());
        }
        seccion.setGrado(request.grado());
        seccion.setLetra(request.letra());
        seccion.setAula(request.aula());
        seccion.setTutor(tutor);
        return SeccionResponse.desde(seccionRepository.save(seccion));
    }

    // Helpers

    @Transactional(readOnly = true)
    public Seccion buscar(Long id) {
        return seccionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("La sección " + id + " no existe"));
    }

    /** El tutor es opcional; si viene, debe existir y ser un perfil Docente. */
    private Docente buscarTutor(Long tutorId) {
        if (tutorId == null) {
            return null;
        }
        return docenteRepository.findById(tutorId)
                .orElseThrow(() -> new NotFoundException("El docente tutor " + tutorId + " no existe"));
    }
}
