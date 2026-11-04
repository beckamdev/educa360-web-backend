package com.educa360.backend.service;

import com.educa360.backend.dto.ActaAlumnoResponse;
import com.educa360.backend.dto.ActaCursoResponse;
import com.educa360.backend.dto.ActaUnidadResponse;
import com.educa360.backend.dto.CalificacionAlumnoResponse;
import com.educa360.backend.dto.CalificacionBulkRequest;
import com.educa360.backend.dto.CalificacionDetalleResponse;
import com.educa360.backend.dto.CalificacionItemRequest;
import com.educa360.backend.dto.CalificacionResumenResponse;
import com.educa360.backend.dto.CalificacionUnidadResponse;
import com.educa360.backend.dto.PromedioCursoResponse;
import com.educa360.backend.dto.PromedioUnidadResponse;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Calificacion;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Unidad;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.CalificacionRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.UnidadRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calificaciones por unidad y criterio (Módulo 6).
 *
 * Reglas que gobierna:
 * - sólo el docente dueño del curso registra, corrige o elimina;
 * - la matrícula debe pertenecer a la sección del curso de la unidad;
 * - el bimestre de la unidad debe estar ABIERTO y el año no CERRADO;
 * - la nota es vigesimal estricta, con dos decimales;
 * - el alumno y el apoderado sólo ven notas de unidades PUBLICADA;
 * - los promedios se calculan por consulta: unidad, bimestre, año y general.
 *
 * Fórmulas:
 * - unidad      = promedio simple de sus notas;
 * - bimestre    = promedio simple de las unidades con notas;
 * - anual curso = promedio simple de los bimestres con notas;
 * - general     = promedio simple de los promedios anuales de los cursos.
 */
@Service
public class CalificacionService {

    private static final BigDecimal NOTA_MINIMA = new BigDecimal("0.00");
    private static final BigDecimal NOTA_MAXIMA = new BigDecimal("20.00");

    private final CalificacionRepository calificacionRepository;
    private final UnidadRepository unidadRepository;
    private final CursoRepository cursoRepository;
    private final BimestreRepository bimestreRepository;
    private final DocenteRepository docenteRepository;
    private final UserRepository userRepository;
    private final EstudianteRepository estudianteRepository;
    private final MatriculaRepository matriculaRepository;
    private final MatriculaService matriculaService;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;

    public CalificacionService(CalificacionRepository calificacionRepository,
                               UnidadRepository unidadRepository,
                               CursoRepository cursoRepository,
                               BimestreRepository bimestreRepository,
                               DocenteRepository docenteRepository,
                               UserRepository userRepository,
                               EstudianteRepository estudianteRepository,
                               MatriculaRepository matriculaRepository,
                               MatriculaService matriculaService,
                               ApoderadoRepository apoderadoRepository,
                               ApoderadoEstudianteRepository vinculoRepository) {
        this.calificacionRepository = calificacionRepository;
        this.unidadRepository = unidadRepository;
        this.cursoRepository = cursoRepository;
        this.bimestreRepository = bimestreRepository;
        this.docenteRepository = docenteRepository;
        this.userRepository = userRepository;
        this.estudianteRepository = estudianteRepository;
        this.matriculaRepository = matriculaRepository;
        this.matriculaService = matriculaService;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
    }

    // ==================== Docente: hoja de la unidad ====================

    @Transactional(readOnly = true)
    public CalificacionUnidadResponse listarUnidad(String email, Long unidadId) {
        Docente docente = docenteDe(email);
        return hojaDe(unidadDelDocente(docente, unidadId));
    }

    @Transactional
    public CalificacionUnidadResponse registrar(String email, Long unidadId,
                                                CalificacionBulkRequest datos) {
        Docente docente = docenteDe(email);
        Unidad unidad = unidadDelDocente(docente, unidadId);
        validarBimestreAbierto(unidad);

        List<CalificacionItemRequest> items = datos.calificaciones();
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("La lista de calificaciones no puede estar vacía");
        }

        Set<String> claves = new HashSet<>();
        for (CalificacionItemRequest item : items) {
            if (item == null) {
                throw new BadRequestException("Ninguna calificación puede ser nula");
            }
            if (item.matriculaId() == null) {
                throw new BadRequestException("La matrícula es obligatoria");
            }
            String criterio = normalizarCriterio(item.criterio());
            validarNota(item.nota());
            if (!claves.add(item.matriculaId() + "|" + criterio)) {
                throw new BadRequestException("La lista no puede repetir la matrícula "
                        + item.matriculaId() + " con el criterio '" + criterio + "'");
            }
        }

        for (CalificacionItemRequest item : items) {
            String criterio = normalizarCriterio(item.criterio());
            Matricula matricula = matriculaRepository.findById(item.matriculaId())
                    .orElseThrow(() -> new NotFoundException(
                            "La matrícula " + item.matriculaId() + " no existe"));
            validarMatricula(unidad, matricula);

            Calificacion calificacion = calificacionRepository
                    .findByMatriculaIdAndUnidadIdAndCriterio(
                            matricula.getId(), unidad.getId(), criterio)
                    .orElseGet(() -> new Calificacion(matricula, unidad, criterio));

            calificacion.setNota(item.nota());
            calificacion.setRegistradoPor(docente);
            calificacionRepository.save(calificacion);
        }

        calificacionRepository.flush();
        return hojaDe(unidad);
    }

    @Transactional
    public void eliminar(String email, Long calificacionId) {
        Docente docente = docenteDe(email);
        Calificacion calificacion = calificacionRepository.findById(calificacionId)
                .orElseThrow(() -> new NotFoundException(
                        "La calificación " + calificacionId + " no existe"));
        if (!calificacion.getUnidad().getCurso().getDocente().getId().equals(docente.getId())) {
            throw new NotFoundException("La calificación " + calificacionId + " no existe");
        }
        validarBimestreAbierto(calificacion.getUnidad());
        calificacionRepository.delete(calificacion);
    }

    // ==================== Docente y ADMIN: acta del curso ====================

    @Transactional(readOnly = true)
    public ActaCursoResponse actaDelDocente(String email, Long cursoId, Long bimestreId) {
        Docente docente = docenteDe(email);
        return actaDe(cursoDelDocente(docente, cursoId), bimestreId);
    }

    @Transactional(readOnly = true)
    public ActaCursoResponse actaAdmin(Long cursoId, Long bimestreId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new NotFoundException("El curso " + cursoId + " no existe"));
        return actaDe(curso, bimestreId);
    }

    // ==================== Alumno, apoderado, secretaría y admin ====================

    @Transactional(readOnly = true)
    public CalificacionResumenResponse resumenDelEstudiante(String email, Long anioId,
                                                            Long bimestreId) {
        Estudiante estudiante = estudianteDe(email);
        return resumenDe(matriculaDe(estudiante, anioId), bimestreId, true);
    }

    @Transactional(readOnly = true)
    public CalificacionResumenResponse resumenDelHijo(String email, Long hijoId, Long anioId,
                                                      Long bimestreId) {
        Apoderado apoderado = apoderadoDe(email);
        ApoderadoEstudiante vinculo = vinculoRepository
                .findByApoderadoIdAndEstudianteId(apoderado.getId(), hijoId)
                .filter(ApoderadoEstudiante::isActivo)
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante " + hijoId + " no está vinculado a este apoderado"));

        Estudiante estudiante = vinculo.getEstudiante();
        if (!estudiante.estaHabilitado()) {
            throw new NotFoundException("El estudiante no está activo");
        }
        return resumenDe(matriculaDe(estudiante, anioId), bimestreId, true);
    }

    @Transactional(readOnly = true)
    public CalificacionResumenResponse resumenDeEstudiante(Long estudianteId, Long anioId,
                                                           Long bimestreId) {
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante " + estudianteId + " no existe"));
        return resumenDe(matriculaDe(estudiante, anioId), bimestreId, false);
    }

    // ==================== Helpers: hoja y acta ====================

    private CalificacionUnidadResponse hojaDe(Unidad unidad) {
        Map<Long, List<Calificacion>> porMatricula = calificacionRepository
                .findByUnidadId(unidad.getId()).stream()
                .collect(Collectors.groupingBy(c -> c.getMatricula().getId()));

        List<CalificacionAlumnoResponse> alumnos = matriculaRepository
                .findBySeccionId(unidad.getCurso().getSeccion().getId()).stream()
                .filter(matricula -> !matricula.estaRetirada())
                .sorted(Comparator.comparing(this::nombreDe))
                .map(matricula -> new CalificacionAlumnoResponse(
                        matricula.getId(),
                        matricula.getEstudiante().getId(),
                        nombreDe(matricula),
                        matricula.getEstudiante().getUser().getDni(),
                        porMatricula.getOrDefault(matricula.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(Calificacion::getCriterio))
                                .map(CalificacionDetalleResponse::desde)
                                .toList()))
                .toList();

        return new CalificacionUnidadResponse(
                unidad.getId(),
                unidad.getNumero(),
                unidad.getTitulo(),
                unidad.getEstado(),
                unidad.getCurso().getId(),
                unidad.getCurso().getMateria().getNombre(),
                unidad.getCurso().getSeccion().getEtiqueta(),
                unidad.getBimestre().getId(),
                unidad.getBimestre().getNombre(),
                alumnos
        );
    }

    private ActaCursoResponse actaDe(Curso curso, Long bimestreId) {
        Bimestre bimestre = bimestreRepository.findById(bimestreId)
                .orElseThrow(() -> new NotFoundException(
                        "El bimestre " + bimestreId + " no existe"));
        if (!bimestre.getAnio().getId().equals(curso.getSeccion().getAnio().getId())) {
            throw new ConflictException("El " + bimestre.getNombre()
                    + " no pertenece al año escolar del curso");
        }

        List<Unidad> unidades = unidadRepository
                .findByCursoIdAndBimestreIdOrderByNumeroAsc(curso.getId(), bimestreId);

        Map<Long, Map<Long, List<BigDecimal>>> notas = new HashMap<>();
        for (Calificacion calificacion : calificacionRepository
                .findByUnidadCursoIdAndUnidadBimestreIdOrderByUnidadNumeroAscCriterioAsc(
                        curso.getId(), bimestreId)) {
            notas.computeIfAbsent(calificacion.getMatricula().getId(), k -> new HashMap<>())
                    .computeIfAbsent(calificacion.getUnidad().getId(), k -> new ArrayList<>())
                    .add(calificacion.getNota());
        }

        List<ActaAlumnoResponse> alumnos = new ArrayList<>();
        for (Matricula matricula : matriculaRepository
                .findBySeccionId(curso.getSeccion().getId()).stream()
                .filter(m -> !m.estaRetirada())
                .sorted(Comparator.comparing(this::nombreDe))
                .toList()) {
            List<ActaUnidadResponse> filas = new ArrayList<>();
            for (Unidad unidad : unidades) {
                List<BigDecimal> valores = notas
                        .getOrDefault(matricula.getId(), Map.of())
                        .getOrDefault(unidad.getId(), List.of());
                filas.add(new ActaUnidadResponse(
                        unidad.getId(), unidad.getNumero(), unidad.getTitulo(),
                        promedio(valores)));
            }
            BigDecimal promedioBimestre = promedio(filas.stream()
                    .map(ActaUnidadResponse::promedio)
                    .filter(Objects::nonNull)
                    .toList());
            alumnos.add(new ActaAlumnoResponse(
                    matricula.getId(),
                    matricula.getEstudiante().getId(),
                    nombreDe(matricula),
                    matricula.getEstudiante().getUser().getDni(),
                    filas,
                    promedioBimestre));
        }

        BigDecimal promedioCurso = promedio(alumnos.stream()
                .map(ActaAlumnoResponse::promedioBimestre)
                .filter(Objects::nonNull)
                .toList());

        return new ActaCursoResponse(
                curso.getId(),
                curso.getMateria().getNombre(),
                curso.getSeccion().getEtiqueta(),
                bimestre.getId(),
                bimestre.getNombre(),
                alumnos,
                promedioCurso
        );
    }

    // ==================== Helpers: resumen del alumno ====================

    private CalificacionResumenResponse resumenDe(Matricula matricula, Long bimestreId,
                                                  boolean soloPublicadas) {
        Bimestre bimestre = null;
        List<Calificacion> calificaciones;

        if (bimestreId != null) {
            bimestre = bimestreRepository.findById(bimestreId)
                    .orElseThrow(() -> new NotFoundException(
                            "El bimestre " + bimestreId + " no existe"));
            if (!bimestre.getAnio().getId().equals(matricula.getAnio().getId())) {
                throw new ConflictException("El " + bimestre.getNombre()
                        + " no pertenece al año de la matrícula");
            }
            calificaciones = calificacionRepository
                    .findByMatriculaIdAndUnidadBimestreIdOrderByUnidadNumeroAscCriterioAsc(
                            matricula.getId(), bimestreId);
        } else {
            calificaciones = calificacionRepository
                    .findByMatriculaIdOrderByUnidadBimestreNumeroAscUnidadNumeroAscCriterioAsc(
                            matricula.getId());
        }

        if (soloPublicadas) {
            calificaciones = calificaciones.stream()
                    .filter(c -> c.getUnidad().estaPublicada())
                    .toList();
        }

        List<CalificacionDetalleResponse> detalle = calificaciones.stream()
                .map(CalificacionDetalleResponse::desde)
                .toList();

        List<PromedioUnidadResponse> promediosUnidad = promediosPorUnidad(calificaciones);
        List<PromedioCursoResponse> promediosCurso = bimestreId != null
                ? promediosCursoDelBimestre(promediosUnidad)
                : promediosCursoDelAnio(promediosUnidad);
        BigDecimal promedioGeneral = promedio(promediosCurso.stream()
                .map(PromedioCursoResponse::promedio)
                .toList());

        User user = matricula.getEstudiante().getUser();
        return new CalificacionResumenResponse(
                matricula.getId(),
                matricula.getEstudiante().getId(),
                user.getNombres() + " " + user.getApellidos(),
                user.getDni(),
                matricula.getSeccion().getEtiqueta(),
                matricula.getAnio().getId(),
                matricula.getAnio().getAnio(),
                bimestre != null ? bimestre.getId() : null,
                bimestre != null ? bimestre.getNombre() : null,
                detalle,
                promediosUnidad,
                promediosCurso,
                promedioGeneral
        );
    }

    private List<PromedioUnidadResponse> promediosPorUnidad(List<Calificacion> calificaciones) {
        Map<Long, List<Calificacion>> porUnidad = calificaciones.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getUnidad().getId(), LinkedHashMap::new, Collectors.toList()));

        return porUnidad.values().stream()
                .map(lista -> {
                    Unidad unidad = lista.get(0).getUnidad();
                    return new PromedioUnidadResponse(
                            unidad.getId(),
                            unidad.getNumero(),
                            unidad.getTitulo(),
                            unidad.getBimestre().getId(),
                            unidad.getBimestre().getNumero(),
                            unidad.getBimestre().getNombre(),
                            unidad.getCurso().getId(),
                            unidad.getCurso().getMateria().getNombre(),
                            promedio(lista.stream().map(Calificacion::getNota).toList()),
                            lista.size());
                })
                .sorted(Comparator.comparing(PromedioUnidadResponse::bimestreNumero)
                        .thenComparing(PromedioUnidadResponse::unidadNumero))
                .toList();
    }

    /** Promedio del bimestre por curso: media simple de las unidades con notas. */
    private List<PromedioCursoResponse> promediosCursoDelBimestre(
            List<PromedioUnidadResponse> promediosUnidad) {
        Map<Long, List<PromedioUnidadResponse>> porCurso = promediosUnidad.stream()
                .collect(Collectors.groupingBy(
                        PromedioUnidadResponse::cursoId, LinkedHashMap::new,
                        Collectors.toList()));

        return porCurso.values().stream()
                .map(lista -> new PromedioCursoResponse(
                        lista.get(0).cursoId(),
                        lista.get(0).materiaNombre(),
                        promedio(lista.stream().map(PromedioUnidadResponse::promedio).toList())))
                .sorted(Comparator.comparing(PromedioCursoResponse::materiaNombre))
                .toList();
    }

    /**
     * Promedio anual por curso: primero el promedio de cada bimestre con notas
     * y después la media simple de esos bimestres.
     */
    private List<PromedioCursoResponse> promediosCursoDelAnio(
            List<PromedioUnidadResponse> promediosUnidad) {
        Map<Long, Map<Long, List<PromedioUnidadResponse>>> porCursoBimestre =
                new LinkedHashMap<>();
        for (PromedioUnidadResponse unidad : promediosUnidad) {
            porCursoBimestre
                    .computeIfAbsent(unidad.cursoId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(unidad.bimestreId(), k -> new ArrayList<>())
                    .add(unidad);
        }

        List<PromedioCursoResponse> resultado = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, List<PromedioUnidadResponse>>> curso
                : porCursoBimestre.entrySet()) {
            List<PromedioUnidadResponse> primeras =
                    curso.getValue().values().iterator().next();
            List<BigDecimal> promediosBimestre = curso.getValue().values().stream()
                    .map(unidades -> promedio(unidades.stream()
                            .map(PromedioUnidadResponse::promedio)
                            .toList()))
                    .toList();
            resultado.add(new PromedioCursoResponse(
                    curso.getKey(),
                    primeras.get(0).materiaNombre(),
                    promedio(promediosBimestre)));
        }
        return resultado.stream()
                .sorted(Comparator.comparing(PromedioCursoResponse::materiaNombre))
                .toList();
    }

    private BigDecimal promedio(List<BigDecimal> valores) {
        List<BigDecimal> limpios = valores.stream().filter(Objects::nonNull).toList();
        if (limpios.isEmpty()) {
            return null;
        }
        BigDecimal suma = limpios.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(limpios.size()), 2, RoundingMode.HALF_UP);
    }

    // ==================== Helpers: validaciones ====================

    private void validarBimestreAbierto(Unidad unidad) {
        Bimestre bimestre = unidad.getBimestre();
        if (bimestre.getAnio().estaCerrado()) {
            throw new ConflictException("El año " + bimestre.getAnio().getAnio()
                    + " está CERRADO: no se registran calificaciones");
        }
        if (bimestre.getEstado() != Bimestre.Estado.ABIERTO) {
            throw new ConflictException("El " + bimestre.getNombre()
                    + " no está ABIERTO: no se registran calificaciones");
        }
    }

    private void validarMatricula(Unidad unidad, Matricula matricula) {
        if (!matricula.getSeccion().getId()
                .equals(unidad.getCurso().getSeccion().getId())) {
            throw new NotFoundException("La matrícula " + matricula.getId()
                    + " no pertenece a este curso");
        }
        if (matricula.estaRetirada()) {
            throw new ConflictException("La matrícula " + matricula.getId()
                    + " está RETIRADA: no se registran calificaciones");
        }
        if (!matricula.getEstudiante().estaHabilitado()) {
            throw new ConflictException("El estudiante de la matrícula " + matricula.getId()
                    + " no está ACTIVO");
        }
    }

    private String normalizarCriterio(String criterio) {
        if (criterio == null || criterio.trim().isEmpty()) {
            throw new BadRequestException("El criterio es obligatorio");
        }
        String limpio = criterio.trim();
        if (limpio.length() > 120) {
            throw new BadRequestException(
                    "El criterio no puede superar los 120 caracteres");
        }
        return limpio;
    }

    private void validarNota(BigDecimal nota) {
        if (nota == null) {
            throw new BadRequestException("La nota es obligatoria");
        }
        if (nota.compareTo(NOTA_MINIMA) < 0 || nota.compareTo(NOTA_MAXIMA) > 0) {
            throw new BadRequestException("La nota debe estar entre 0.00 y 20.00");
        }
        if (nota.scale() > 2) {
            throw new BadRequestException("La nota admite como máximo dos decimales");
        }
    }

    // ==================== Helpers: identidad ====================

    private Docente docenteDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return docenteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Docente"));
    }

    private Curso cursoDelDocente(Docente docente, Long cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new NotFoundException("El curso " + cursoId + " no existe"));
        if (!curso.getDocente().getId().equals(docente.getId())) {
            throw new NotFoundException("El curso " + cursoId + " no existe");
        }
        return curso;
    }

    private Unidad unidadDelDocente(Docente docente, Long unidadId) {
        Unidad unidad = unidadRepository.findById(unidadId)
                .orElseThrow(() -> new NotFoundException(
                        "La unidad " + unidadId + " no existe"));
        if (!unidad.getCurso().getDocente().getId().equals(docente.getId())) {
            throw new NotFoundException("La unidad " + unidadId + " no existe");
        }
        return unidad;
    }

    private Estudiante estudianteDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Estudiante estudiante = estudianteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Estudiante"));
        if (!estudiante.estaHabilitado()) {
            throw new NotFoundException("El estudiante no está activo");
        }
        return estudiante;
    }

    private Apoderado apoderadoDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return apoderadoRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Apoderado"));
    }

    private Matricula matriculaDe(Estudiante estudiante, Long anioId) {
        if (anioId != null) {
            return matriculaRepository.findByEstudianteIdAndAnioId(estudiante.getId(), anioId)
                    .orElseThrow(() -> new NotFoundException("El estudiante "
                            + estudiante.getId() + " no tiene matrícula en el año " + anioId));
        }
        return matriculaService.vigenteDe(estudiante.getId())
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante no tiene matrícula en el año vigente"));
    }

    private String nombreDe(Matricula matricula) {
        User user = matricula.getEstudiante().getUser();
        return user.getNombres() + " " + user.getApellidos();
    }
}
