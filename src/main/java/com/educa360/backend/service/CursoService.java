package com.educa360.backend.service;

import com.educa360.backend.dto.CursoRequest;
import com.educa360.backend.dto.CursoResponse;
import com.educa360.backend.dto.HorarioBloqueResponse;
import com.educa360.backend.dto.HorarioRequest;
import com.educa360.backend.dto.HorarioSeccionResponse;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.HorarioBloque;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cursos (§5, catálogo académico) = "el curso" del colegio: Matemática · 3°A · profe X.
 *
 * Reglas de creación:
 * - única por (materia, sección): nadie más da Matemáticas en 3°A → 409;
 * - el docente debe tener la materia habilitada (Docente.materias) → 409;
 * - la materia debe estar activa (activa=false frena nuevos cursos) → 409.
 *
 * El horario es 1:N ({@link HorarioBloque}): un curso se ve varias veces por
 * semana. Se reemplaza por hoja entera y valida que ni la sección ni el
 * docente queden en dos sitios a la vez el mismo día.
 */
@Service
public class CursoService {

    private final CursoRepository cursoRepository;
    private final HorarioBloqueRepository horarioRepository;
    private final DocenteRepository docenteRepository;
    private final EstudianteRepository estudianteRepository;
    private final UserRepository userRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final UnidadRepository unidadRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final CalificacionRepository calificacionRepository;
    private final MaterialClaseRepository materialClaseRepository;
    private final MateriaService materiaService;
    private final SeccionService seccionService;
    private final MatriculaService matriculaService;

    public CursoService(CursoRepository cursoRepository,
                        HorarioBloqueRepository horarioRepository,
                        DocenteRepository docenteRepository,
                        EstudianteRepository estudianteRepository,
                        UserRepository userRepository,
                        ApoderadoRepository apoderadoRepository,
                        ApoderadoEstudianteRepository vinculoRepository,
                        UnidadRepository unidadRepository,
                        AsistenciaRepository asistenciaRepository,
                        CalificacionRepository calificacionRepository,
                        MaterialClaseRepository materialClaseRepository,
                        MateriaService materiaService,
                        SeccionService seccionService,
                        MatriculaService matriculaService) {
        this.cursoRepository = cursoRepository;
        this.horarioRepository = horarioRepository;
        this.docenteRepository = docenteRepository;
        this.estudianteRepository = estudianteRepository;
        this.userRepository = userRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
        this.unidadRepository = unidadRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.calificacionRepository = calificacionRepository;
        this.materialClaseRepository = materialClaseRepository;
        this.materiaService = materiaService;
        this.seccionService = seccionService;
        this.matriculaService = matriculaService;
    }

    @Transactional
    public CursoResponse crear(CursoRequest request) {
        Docente docente = docenteRepository.findById(request.docenteId())
                .orElseThrow(() -> new NotFoundException("El docente " + request.docenteId() + " no existe"));
        Materia materia = materiaService.buscar(request.materiaId());
        Seccion seccion = seccionService.buscar(request.seccionId());

        if (seccion.getAnio().estaCerrado()) {
            throw new ConflictException("El año " + seccion.getAnio().getAnio()
                    + " está CERRADO: no se pueden crear cursos");
        }
        if (!materia.isActiva()) {
            throw new ConflictException("La materia '" + materia.getNombre()
                    + "' está desactivada: reactívala antes de crear el curso");
        }
        if (!docenteRepository.existsByIdAndMaterias_Id(docente.getId(), materia.getId())) {
            throw new ConflictException("La materia '" + materia.getNombre()
                    + "' no está habilitada para ese docente: habilítala antes en /admin/docentes/{id}/materias");
        }
        if (cursoRepository.existsByMateriaAndSeccion(materia, seccion)) {
            throw new ConflictException("La materia '" + materia.getNombre()
                    + "' ya tiene curso en la sección " + seccion.getEtiqueta());
        }
        return aRespuestas(List.of(cursoRepository.save(
                new Curso(docente, materia, seccion)))).get(0);
    }

    /** Filtrable por sección (?seccionId=) o por docente (?docenteId=). */
    @Transactional(readOnly = true)
    public List<CursoResponse> listar(Long seccionId, Long docenteId) {
        List<Curso> cursos;
        if (seccionId != null) {
            cursos = cursoRepository.findBySeccionId(seccionId);
        } else if (docenteId != null) {
            cursos = cursoRepository.findByDocenteId(docenteId);
        } else {
            cursos = cursoRepository.findAll();
        }
        return aRespuestas(cursos);
    }

    /** Los cursos de la sección del ESTUDIANTE (matrícula vigente). Sin matrícula: lista vacía. */
    @Transactional(readOnly = true)
    public List<CursoResponse> listarDelEstudiante(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Estudiante estudiante = estudianteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Estudiante"));
        if (!estudiante.estaHabilitado()) {
            return List.of(); // retirado/trasladado/egresado: ya no cursa
        }
        return matriculaService.vigenteDe(estudiante.getId())
                .map(matricula -> cursoRepository.findBySeccionId(matricula.getSeccion().getId()))
                .map(this::aRespuestas)
                .orElse(List.of());
    }

    /**
     * Los cursos del DOCENTE autenticado (zona /docente/cursos).
     *
     * La ruta no admite filtros: el dueño de los datos sale del token, que es
     * quien no puede falsear la consulta — así el docente sólo ve SUS cursos
     * (§5, acceso a datos por perfil).
     */
    @Transactional(readOnly = true)
    public List<CursoResponse> listarDelDocente(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Docente docente = docenteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Docente"));
        return aRespuestas(cursoRepository.findByDocenteId(docente.getId()));
    }

    /**
     * Quitar el curso. Es una baja física a propósito: no es histórico de
     * personas (no se desactiva como User), sino configuración del período —
     * las notas de ese curso deciden si exigen 409.
     */
    @Transactional
    public void eliminar(Long id) {
        Curso curso = buscar(id);
        if (curso.getSeccion().getAnio().estaCerrado()) {
            throw new ConflictException("El año " + curso.getSeccion().getAnio().getAnio()
                    + " está CERRADO: no se pueden eliminar cursos");
        }
        if (calificacionRepository.existsByUnidadCursoId(curso.getId())) {
            throw new ConflictException("El curso ya tiene calificaciones: "
                    + "no se puede eliminar");
        }
        if (unidadRepository.existsByCursoId(curso.getId())) {
            throw new ConflictException("El curso ya tiene planificación (unidades): "
                    + "elimínalas antes de borrar el curso");
        }
        if (asistenciaRepository.existsByBloqueCursoId(curso.getId())) {
            throw new ConflictException("El curso ya tiene asistencias: "
                    + "no se puede eliminar");
        }
        if (materialClaseRepository.existsByCursoIdAndActivoTrue(curso.getId())) {
            throw new ConflictException("El curso ya tiene materiales: "
                    + "no se puede eliminar");
        }
        horarioRepository.deleteByCurso(curso); // primero los bloques: tienen FK al curso
        cursoRepository.delete(curso);
    }

    // ==================== Horario ====================

    @Transactional(readOnly = true)
    public List<HorarioBloqueResponse> listarHorario(Long cursoId) {
        buscar(cursoId); // 404 si el curso no existe
        return horarioRepository.findByCursoId(cursoId).stream()
                .map(HorarioBloqueResponse::desde)
                .toList();
    }

    /**
     * Horario de un curso para su DOCENTE dueño. La identidad sale del token:
     * un docente no puede consultar el horario de un curso ajeno.
     */
    @Transactional(readOnly = true)
    public List<HorarioBloqueResponse> listarHorarioDelDocente(String email, Long cursoId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Docente docente = docenteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Docente"));
        Curso curso = buscar(cursoId);
        if (!curso.getDocente().getId().equals(docente.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "El curso " + cursoId + " no pertenece a este docente");
        }
        return horarioRepository.findByCursoId(cursoId).stream()
                .map(HorarioBloqueResponse::desde)
                .toList();
    }

    /**
     * Semana completa del DOCENTE autenticado: todos los bloques de SUS cursos
     * en una sola petición, con materia y sección. Permite pintar "clases de
     * hoy" sin una llamada por curso. La identidad sale del token.
     */
    @Transactional(readOnly = true)
    public List<HorarioSeccionResponse> listarHorarioDelDocente(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Docente docente = docenteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Docente"));
        return ordenar(horarioRepository.findByCursoDocenteId(docente.getId()));
    }

    /**
     * Horario completo de una SECCIÓN: todos los bloques de sus cursos en una
     * sola petición, ordenados por día (LUNES→SÁBADO) y hora de inicio.
     *
     * 404 si la sección no existe. La consulta trae materia y docente con
     * EntityGraph para no caer en N+1 (open-in-view=false).
     */
    @Transactional(readOnly = true)
    public List<HorarioSeccionResponse> listarHorarioDeSeccion(Long seccionId) {
        seccionService.buscar(seccionId); // 404 si no existe
        return horarioDe(seccionId);
    }

    /**
     * Horario de la sección vigente del ESTUDIANTE autenticado.
     *
     * La identidad sale del token: no recibe ids, no puede pedir el horario de
     * otra sección. Sin matrícula en el año abierto (o con el alumno retirado)
     * devuelve lista vacía — igual que {@code /estudiante/cursos}: es un alumno
     * aún no inscrito, no un error.
     */
    @Transactional(readOnly = true)
    public List<HorarioSeccionResponse> listarHorarioDelEstudiante(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Estudiante estudiante = estudianteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Estudiante"));
        if (!estudiante.estaHabilitado()) {
            return List.of(); // retirado/trasladado/egresado: ya no cursa
        }
        return matriculaService.vigenteDe(estudiante.getId())
                .map(matricula -> horarioDe(matricula.getSeccion().getId()))
                .orElse(List.of());
    }

    /**
     * Horario de la sección vigente de un HIJO del apoderado autenticado.
     *
     * El vínculo lo crea Secretaría: si el hijo no está vinculado o no está
     * activo responde 404 (mismo criterio que asistencias, notas y materiales).
     * Sin matrícula en el año abierto devuelve lista vacía: el hijo existe
     * antes de ser inscrito.
     */
    @Transactional(readOnly = true)
    public List<HorarioSeccionResponse> listarHorarioDelHijo(String email, Long hijoId) {
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
        return matriculaService.vigenteDe(estudiante.getId())
                .map(matricula -> horarioDe(matricula.getSeccion().getId()))
                .orElse(List.of());
    }

    /**
     * Bloques de una sección con materia y docente, ordenados por día
     * (LUNES→SÁBADO) y hora. El orden va en Java a propósito: con
     * EnumType.STRING un ORDER BY dia saldría alfabético.
     */
    private List<HorarioSeccionResponse> horarioDe(Long seccionId) {
        return ordenar(horarioRepository.findByCursoSeccionId(seccionId));
    }

    /**
     * Cursos a DTO con el tamaño de su sección. Un solo conteo agrupado por
     * sección evita N consultas en los listados.
     */
    private List<CursoResponse> aRespuestas(List<Curso> cursos) {
        if (cursos.isEmpty()) {
            return List.of();
        }
        Set<Long> seccionIds = cursos.stream()
                .map(curso -> curso.getSeccion().getId())
                .collect(Collectors.toSet());
        Map<Long, Long> conteos = matriculaService.contarAlumnosPorSeccion(seccionIds);
        return cursos.stream()
                .map(curso -> CursoResponse.desde(curso,
                        conteos.getOrDefault(curso.getSeccion().getId(), 0L)))
                .toList();
    }

    private List<HorarioSeccionResponse> ordenar(List<HorarioBloque> bloques) {
        return bloques.stream()
                .sorted(Comparator
                        .comparingInt((HorarioBloque bloque) -> bloque.getDia().ordinal())
                        .thenComparing(HorarioBloque::getHoraInicio))
                .map(HorarioSeccionResponse::desde)
                .toList();
    }

    private Apoderado apoderadoDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return apoderadoRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Apoderado"));
    }

    /**
     * Reemplaza la hoja completa del horario del curso (misma filosofía que
     * las habilitaciones: se pinta entera, el resultado no depende del previo).
     *
     * Reglas → 400 si un bloque termina antes de empezar; 409 si se solapa con
     * otro curso de la misma sección o del mismo docente, el mismo día.
     * Los bloques consecutivos (09:00 fin / 09:00 inicio) NO se solapan.
     */
    @Transactional
    public List<HorarioBloqueResponse> reemplazarHorario(Long cursoId, HorarioRequest request) {
        Curso curso = buscar(cursoId);

        if (curso.getSeccion().getAnio().estaCerrado()) {
            throw new ConflictException("El año " + curso.getSeccion().getAnio().getAnio()
                    + " está CERRADO: no se puede modificar el horario");
        }
        if (asistenciaRepository.existsByBloqueCursoId(curso.getId())) {
            throw new ConflictException("El curso ya tiene asistencias: "
                    + "no se puede reemplazar el horario");
        }

        List<HorarioBloque> nuevos = request.bloques().stream()
                .map(b -> new HorarioBloque(curso, b.dia(), b.horaInicio(), b.horaFin()))
                .toList();

        for (HorarioBloque bloque : nuevos) {
            if (!bloque.getHoraFin().isAfter(bloque.getHoraInicio())) {
                throw new BadRequestException("El bloque del " + bloque.getDia()
                        + " termina antes (o a la misma hora) de empezar");
            }
        }

        // La hoja nueva no puede solaparse consigo misma (ni repetir inicio)
        for (int i = 0; i < nuevos.size(); i++) {
            for (int j = i + 1; j < nuevos.size(); j++) {
                HorarioBloque a = nuevos.get(i);
                HorarioBloque b = nuevos.get(j);
                if (a.getDia() == b.getDia() && a.getHoraInicio().equals(b.getHoraInicio())) {
                    throw new ConflictException("Bloque repetido: " + a.getDia() + " " + a.getHoraInicio());
                }
                if (a.seSolapaCon(b)) {
                    throw new ConflictException("Los bloques del " + a.getDia() + " entre "
                            + a.getHoraInicio() + " y " + b.getHoraFin() + " se solapan");
                }
            }
        }

        // Solapes contra los cursos VIVOS de la sección y del docente:
        // la hoja propia se reemplaza entera, así que se excluye al propio curso.
        for (HorarioBloque bloque : nuevos) {
            validarSolapes(bloque, curso);
        }

        List<HorarioBloque> previos = horarioRepository.findByCursoId(cursoId);
        horarioRepository.deleteAll(previos); // la hoja vieja se va entera
        // Los deletes a BD ANTES de encolar los inserts: sin este flush, el unique
        // (curso, día, hora_inicio) ve primero la hoja nueva y responde 409 aunque
        // la vieja esté en camino de borrarse (re-PUT idéntico = falso conflicto).
        horarioRepository.flush();
        return horarioRepository.saveAll(nuevos).stream()
                .map(HorarioBloqueResponse::desde)
                .toList();
    }

    // Helpers

    @Transactional(readOnly = true)
    public Curso buscar(Long id) {
        return cursoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("El curso " + id + " no existe"));
    }

    private void validarSolapes(HorarioBloque nuevo, Curso curso) {
        List<HorarioBloque> deLaSeccion =
                horarioRepository.findByCursoSeccionIdAndDia(curso.getSeccion().getId(), nuevo.getDia());
        for (HorarioBloque existente : deLaSeccion) {
            if (!existente.getCurso().getId().equals(curso.getId()) && nuevo.seSolapaCon(existente)) {
                throw new ConflictException("La sección " + curso.getSeccion().getEtiqueta()
                        + " ya tiene el curso '" + existente.getCurso().getMateria().getNombre()
                        + "' el " + nuevo.getDia() + " de " + existente.getHoraInicio()
                        + " a " + existente.getHoraFin());
            }
        }

        List<HorarioBloque> delDocente =
                horarioRepository.findByCursoDocenteIdAndDia(curso.getDocente().getId(), nuevo.getDia());
        for (HorarioBloque existente : delDocente) {
            if (!existente.getCurso().getId().equals(curso.getId()) && nuevo.seSolapaCon(existente)) {
                throw new ConflictException("El docente ya tiene el curso '"
                        + existente.getCurso().getMateria().getNombre() + "' ("
                        + existente.getCurso().getSeccion().getEtiqueta() + ") el " + nuevo.getDia()
                        + " de " + existente.getHoraInicio() + " a " + existente.getHoraFin());
            }
        }
    }
}
