package com.educa360.backend.service;

import com.educa360.backend.dto.ComunicadoCreateRequest;
import com.educa360.backend.dto.ComunicadoResponse;
import com.educa360.backend.dto.ComunicadoUpdateRequest;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Comunicado;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.Secretaria;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.ComunicadoRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.SeccionRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comunicados (Módulo 9).
 *
 * Reglas:
 * - ADMIN publica generales o por sección y puede editar/eliminar cualquiera;
 * - SECRETARIA publica solo generales y solo gestiona los suyos;
 * - DOCENTE publica en secciones donde dicta clase o es tutor, y solo gestiona
 *   los suyos;
 * - alumno y apoderado leen generales y los de su sección;
 * - un año CERRADO es solo lectura;
 * - la baja es lógica.
 */
@Service
public class ComunicadoService {

    private static final int MAX_CONTENIDO = 5000;

    private final ComunicadoRepository comunicadoRepository;
    private final UserRepository userRepository;
    private final AnioEscolarRepository anioRepository;
    private final SeccionRepository seccionRepository;
    private final CursoRepository cursoRepository;
    private final DocenteRepository docenteRepository;
    private final SecretariaRepository secretariaRepository;
    private final EstudianteRepository estudianteRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final MatriculaService matriculaService;
    private final NotificacionService notificacionService;

    public ComunicadoService(ComunicadoRepository comunicadoRepository,
                             UserRepository userRepository,
                             AnioEscolarRepository anioRepository,
                             SeccionRepository seccionRepository,
                             CursoRepository cursoRepository,
                             DocenteRepository docenteRepository,
                             SecretariaRepository secretariaRepository,
                             EstudianteRepository estudianteRepository,
                             ApoderadoRepository apoderadoRepository,
                             ApoderadoEstudianteRepository vinculoRepository,
                             MatriculaService matriculaService,
                             NotificacionService notificacionService) {
        this.comunicadoRepository = comunicadoRepository;
        this.userRepository = userRepository;
        this.anioRepository = anioRepository;
        this.seccionRepository = seccionRepository;
        this.cursoRepository = cursoRepository;
        this.docenteRepository = docenteRepository;
        this.secretariaRepository = secretariaRepository;
        this.estudianteRepository = estudianteRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
        this.matriculaService = matriculaService;
        this.notificacionService = notificacionService;
    }

    // ==================== ADMIN ====================

    @Transactional(readOnly = true)
    public List<ComunicadoResponse> listarAdmin(Long anioId, Long seccionId) {
        AnioEscolar anio = anioId != null ? anioDe(anioId) : null;
        Seccion seccion = seccionId != null ? seccionDe(seccionId) : null;
        if (anio != null && seccion != null
                && !seccion.getAnio().getId().equals(anio.getId())) {
            throw new BadRequestException("La sección no pertenece al año indicado");
        }

        List<Comunicado> lista;
        if (anio != null && seccion != null) {
            lista = comunicadoRepository
                    .findByAnioIdAndSeccionIdAndActivoTrueOrderByCreatedAtDesc(
                            anio.getId(), seccion.getId());
        } else if (anio != null) {
            lista = comunicadoRepository
                    .findByAnioIdAndActivoTrueOrderByCreatedAtDesc(anio.getId());
        } else if (seccion != null) {
            lista = comunicadoRepository
                    .findBySeccionIdAndActivoTrueOrderByCreatedAtDesc(seccion.getId());
        } else {
            lista = comunicadoRepository.findByActivoTrueOrderByCreatedAtDesc();
        }
        return respuestas(lista);
    }

    @Transactional
    public ComunicadoResponse crearAdmin(String email, ComunicadoCreateRequest datos) {
        User autor = userDe(email);
        AnioEscolar anio = anioDe(datos.anioId());
        validarAnioEditable(anio);
        Seccion seccion = seccionDelAnio(datos.seccionId(), anio);

        Comunicado comunicado = new Comunicado(autor, anio, seccion,
                texto(datos.contenido()));
        Comunicado guardado = comunicadoRepository.save(comunicado);
        notificacionService.notificarComunicado(guardado);
        return ComunicadoResponse.desde(guardado);
    }

    @Transactional
    public ComunicadoResponse actualizarAdmin(String email, Long id,
                                              ComunicadoUpdateRequest datos) {
        userDe(email);
        Comunicado comunicado = buscarActivo(id);
        validarAnioEditable(comunicado.getAnio());

        Seccion seccion = seccionDelAnio(datos.seccionId(), comunicado.getAnio());
        comunicado.setSeccion(seccion);
        comunicado.setContenido(texto(datos.contenido()));
        return ComunicadoResponse.desde(comunicadoRepository.save(comunicado));
    }

    @Transactional
    public void eliminarAdmin(String email, Long id) {
        userDe(email);
        Comunicado comunicado = buscarActivo(id);
        validarAnioEditable(comunicado.getAnio());
        comunicado.setActivo(false);
        comunicadoRepository.save(comunicado);
    }

    // ==================== SECRETARÍA ====================

    @Transactional(readOnly = true)
    public List<ComunicadoResponse> listarSecretaria(String email, Long anioId) {
        secretariaDe(email);
        List<Comunicado> lista;
        if (anioId != null) {
            anioDe(anioId); // 404 si el año no existe
            lista = comunicadoRepository
                    .findByAnioIdAndSeccionIsNullAndActivoTrueOrderByCreatedAtDesc(anioId);
        } else {
            lista = comunicadoRepository.findBySeccionIsNullAndActivoTrueOrderByCreatedAtDesc();
        }
        return respuestas(lista);
    }

    @Transactional
    public ComunicadoResponse crearSecretaria(String email, ComunicadoCreateRequest datos) {
        User autor = secretariaDe(email).getUser();
        if (datos.seccionId() != null) {
            throw new BadRequestException(
                    "Secretaría solo publica comunicados generales");
        }
        AnioEscolar anio = anioDe(datos.anioId());
        validarAnioEditable(anio);

        Comunicado comunicado = new Comunicado(autor, anio, null,
                texto(datos.contenido()));
        Comunicado guardado = comunicadoRepository.save(comunicado);
        notificacionService.notificarComunicado(guardado);
        return ComunicadoResponse.desde(guardado);
    }

    @Transactional
    public ComunicadoResponse actualizarSecretaria(String email, Long id,
                                                   ComunicadoUpdateRequest datos) {
        Secretaria secretaria = secretariaDe(email);
        Comunicado comunicado = buscarActivo(id);
        if (!comunicado.esGeneral()) {
            throw new NotFoundException("El comunicado " + id + " no existe");
        }
        if (datos.seccionId() != null) {
            throw new BadRequestException(
                    "Secretaría solo publica comunicados generales");
        }
        validarAnioEditable(comunicado.getAnio());
        validarAutor(comunicado, secretaria.getUser());

        comunicado.setContenido(texto(datos.contenido()));
        return ComunicadoResponse.desde(comunicadoRepository.save(comunicado));
    }

    @Transactional
    public void eliminarSecretaria(String email, Long id) {
        Secretaria secretaria = secretariaDe(email);
        Comunicado comunicado = buscarActivo(id);
        if (!comunicado.esGeneral()) {
            throw new NotFoundException("El comunicado " + id + " no existe");
        }
        validarAnioEditable(comunicado.getAnio());
        validarAutor(comunicado, secretaria.getUser());

        comunicado.setActivo(false);
        comunicadoRepository.save(comunicado);
    }

    // ==================== DOCENTE ====================

    @Transactional(readOnly = true)
    public List<ComunicadoResponse> listarDocente(String email, Long anioId,
                                                  Long seccionId) {
        Docente docente = docenteDe(email);
        AnioEscolar anio = anioId != null ? anioDe(anioId) : null;
        Set<Seccion> secciones = seccionesDelDocente(docente);

        if (seccionId != null) {
            Seccion seccion = seccionDe(seccionId);
            if (!secciones.contains(seccion)) {
                throw new NotFoundException("La sección " + seccionId + " no existe");
            }
            if (anio != null && !seccion.getAnio().getId().equals(anio.getId())) {
                throw new BadRequestException("La sección no pertenece al año indicado");
            }
            Long anioFiltro = anio != null ? anio.getId() : seccion.getAnio().getId();
            return respuestas(comunicadoRepository.findVisibles(
                    List.of(anioFiltro), List.of(seccion.getId())));
        }

        if (secciones.isEmpty()) {
            List<Comunicado> lista = anio != null
                    ? comunicadoRepository
                            .findByAnioIdAndSeccionIsNullAndActivoTrueOrderByCreatedAtDesc(
                                    anio.getId())
                    : comunicadoRepository
                            .findBySeccionIsNullAndActivoTrueOrderByCreatedAtDesc();
            return respuestas(lista);
        }

        Set<Long> anios = anio != null
                ? Set.of(anio.getId())
                : secciones.stream()
                        .map(seccion -> seccion.getAnio().getId())
                        .collect(Collectors.toSet());
        Set<Long> seccionIds = secciones.stream()
                .map(Seccion::getId)
                .collect(Collectors.toSet());
        return respuestas(comunicadoRepository.findVisibles(anios, seccionIds));
    }

    @Transactional
    public ComunicadoResponse crearDocente(String email, ComunicadoCreateRequest datos) {
        Docente docente = docenteDe(email);
        if (datos.seccionId() == null) {
            throw new BadRequestException("El docente debe indicar la sección");
        }
        AnioEscolar anio = anioDe(datos.anioId());
        validarAnioEditable(anio);
        Seccion seccion = seccionDelAnio(datos.seccionId(), anio);
        validarDocenteEnSeccion(docente, seccion);

        Comunicado comunicado = new Comunicado(docente.getUser(), anio, seccion,
                texto(datos.contenido()));
        Comunicado guardado = comunicadoRepository.save(comunicado);
        notificacionService.notificarComunicado(guardado);
        return ComunicadoResponse.desde(guardado);
    }

    @Transactional
    public ComunicadoResponse actualizarDocente(String email, Long id,
                                                ComunicadoUpdateRequest datos) {
        Docente docente = docenteDe(email);
        Comunicado comunicado = buscarActivo(id);
        if (comunicado.esGeneral()) {
            throw new NotFoundException("El comunicado " + id + " no existe");
        }
        validarAnioEditable(comunicado.getAnio());
        validarAutor(comunicado, docente.getUser());

        Seccion seccion = datos.seccionId() != null
                ? seccionDelAnio(datos.seccionId(), comunicado.getAnio())
                : comunicado.getSeccion();
        validarDocenteEnSeccion(docente, seccion);

        comunicado.setSeccion(seccion);
        comunicado.setContenido(texto(datos.contenido()));
        return ComunicadoResponse.desde(comunicadoRepository.save(comunicado));
    }

    @Transactional
    public void eliminarDocente(String email, Long id) {
        Docente docente = docenteDe(email);
        Comunicado comunicado = buscarActivo(id);
        if (comunicado.esGeneral()) {
            throw new NotFoundException("El comunicado " + id + " no existe");
        }
        validarAnioEditable(comunicado.getAnio());
        validarAutor(comunicado, docente.getUser());
        validarDocenteEnSeccion(docente, comunicado.getSeccion());

        comunicado.setActivo(false);
        comunicadoRepository.save(comunicado);
    }

    // ==================== ESTUDIANTE Y APODERADO ====================

    @Transactional(readOnly = true)
    public List<ComunicadoResponse> listarEstudiante(String email) {
        Estudiante estudiante = estudianteDe(email);
        Matricula matricula = matriculaService.vigenteDe(estudiante.getId())
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante no tiene matrícula en el año vigente"));

        return respuestas(comunicadoRepository.findVisibles(
                List.of(matricula.getAnio().getId()),
                List.of(matricula.getSeccion().getId())));
    }

    @Transactional(readOnly = true)
    public List<ComunicadoResponse> listarApoderado(String email) {
        Apoderado apoderado = apoderadoDe(email);
        List<Matricula> matriculas = vinculoRepository
                .findByApoderadoIdAndActivoTrue(apoderado.getId())
                .stream()
                .map(ApoderadoEstudiante::getEstudiante)
                .filter(Estudiante::estaHabilitado)
                .map(estudiante -> matriculaService.vigenteDe(estudiante.getId()))
                .flatMap(Optional::stream)
                .toList();

        if (matriculas.isEmpty()) {
            return List.of();
        }

        Set<Long> anios = matriculas.stream()
                .map(matricula -> matricula.getAnio().getId())
                .collect(Collectors.toSet());
        Set<Long> seccionIds = matriculas.stream()
                .map(matricula -> matricula.getSeccion().getId())
                .collect(Collectors.toSet());
        return respuestas(comunicadoRepository.findVisibles(anios, seccionIds));
    }

    // ==================== Helpers ====================

    private List<ComunicadoResponse> respuestas(List<Comunicado> comunicados) {
        return comunicados.stream().map(ComunicadoResponse::desde).toList();
    }

    private Comunicado buscarActivo(Long id) {
        Comunicado comunicado = comunicadoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "El comunicado " + id + " no existe"));
        if (!comunicado.isActivo()) {
            throw new NotFoundException("El comunicado " + id + " no existe");
        }
        return comunicado;
    }

    private AnioEscolar anioDe(Long id) {
        return anioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "El año escolar " + id + " no existe"));
    }

    private Seccion seccionDe(Long id) {
        return seccionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "La sección " + id + " no existe"));
    }

    private Seccion seccionDelAnio(Long seccionId, AnioEscolar anio) {
        if (seccionId == null) {
            return null;
        }
        Seccion seccion = seccionDe(seccionId);
        if (!seccion.getAnio().getId().equals(anio.getId())) {
            throw new BadRequestException("La sección no pertenece al año indicado");
        }
        return seccion;
    }

    private void validarAnioEditable(AnioEscolar anio) {
        if (anio.estaCerrado()) {
            throw new ConflictException("El año " + anio.getAnio()
                    + " está CERRADO: es solo lectura");
        }
    }

    private void validarAutor(Comunicado comunicado, User user) {
        if (!comunicado.getAutor().getId().equals(user.getId())) {
            throw new NotFoundException("El comunicado " + comunicado.getId() + " no existe");
        }
    }

    private Set<Seccion> seccionesDelDocente(Docente docente) {
        Set<Seccion> secciones = new LinkedHashSet<>();
        cursoRepository.findByDocenteId(docente.getId())
                .stream()
                .map(Curso::getSeccion)
                .forEach(secciones::add);
        secciones.addAll(seccionRepository.findByTutorId(docente.getId()));
        return secciones;
    }

    private void validarDocenteEnSeccion(Docente docente, Seccion seccion) {
        boolean dictaClase = cursoRepository.findByDocenteId(docente.getId())
                .stream()
                .anyMatch(curso -> curso.getSeccion().getId().equals(seccion.getId()));
        boolean esTutor = seccion.getTutor() != null
                && seccion.getTutor().getId().equals(docente.getId());
        if (!dictaClase && !esTutor) {
            throw new NotFoundException("La sección " + seccion.getId() + " no existe");
        }
    }

    private User userDe(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private Docente docenteDe(String email) {
        User user = userDe(email);
        return docenteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Docente"));
    }

    private Secretaria secretariaDe(String email) {
        User user = userDe(email);
        return secretariaRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Secretaria"));
    }

    private Estudiante estudianteDe(String email) {
        User user = userDe(email);
        Estudiante estudiante = estudianteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Estudiante"));
        if (!estudiante.estaHabilitado()) {
            throw new NotFoundException("El estudiante no está activo");
        }
        return estudiante;
    }

    private Apoderado apoderadoDe(String email) {
        User user = userDe(email);
        return apoderadoRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Apoderado"));
    }

    private String texto(String contenido) {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new BadRequestException("El contenido es obligatorio");
        }
        String limpio = contenido.trim();
        if (limpio.length() > MAX_CONTENIDO) {
            throw new BadRequestException(
                    "El contenido no puede superar los " + MAX_CONTENIDO + " caracteres");
        }
        return limpio;
    }
}
