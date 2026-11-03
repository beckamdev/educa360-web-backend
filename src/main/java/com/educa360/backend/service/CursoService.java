package com.educa360.backend.service;

import com.educa360.backend.dto.CursoRequest;
import com.educa360.backend.dto.CursoResponse;
import com.educa360.backend.dto.HorarioBloqueResponse;
import com.educa360.backend.dto.HorarioRequest;
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
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.HorarioBloqueRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final MateriaService materiaService;
    private final SeccionService seccionService;
    private final MatriculaService matriculaService;

    public CursoService(CursoRepository cursoRepository,
                        HorarioBloqueRepository horarioRepository,
                        DocenteRepository docenteRepository,
                        EstudianteRepository estudianteRepository,
                        UserRepository userRepository,
                        MateriaService materiaService,
                        SeccionService seccionService,
                        MatriculaService matriculaService) {
        this.cursoRepository = cursoRepository;
        this.horarioRepository = horarioRepository;
        this.docenteRepository = docenteRepository;
        this.estudianteRepository = estudianteRepository;
        this.userRepository = userRepository;
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
        return CursoResponse.desde(cursoRepository.save(new Curso(docente, materia, seccion)));
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
        return cursos.stream().map(CursoResponse::desde).toList();
    }

    /** Los cursos de la sección del ESTUDIANTE (matrícula vigente). Sin matrícula: lista vacía. */
    @Transactional(readOnly = true)
    public List<CursoResponse> listarDelEstudiante(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Estudiante estudiante = estudianteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("El usuario no tiene perfil Estudiante"));
        return matriculaService.vigenteDe(estudiante.getId())
                .map(matricula -> cursoRepository.findBySeccionId(matricula.getSeccion().getId()))
                .orElse(List.of())
                .stream()
                .map(CursoResponse::desde)
                .toList();
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
        return cursoRepository.findByDocenteId(docente.getId()).stream()
                .map(CursoResponse::desde)
                .toList();
    }

    /**
     * Quitar el curso. Es una baja física a propósito: no es histórico de
     * personas (no se desactiva como User), sino configuración del período —
     * las notas de ese curso (futuro módulo) decidirán si exigen 404.
     */
    @Transactional
    public void eliminar(Long id) {
        Curso curso = buscar(id);
        if (curso.getSeccion().getAnio().estaCerrado()) {
            throw new ConflictException("El año " + curso.getSeccion().getAnio().getAnio()
                    + " está CERRADO: no se pueden eliminar cursos");
        }
        // TODO M5/M6: bloquear con 409 si el curso ya tiene asistencias,
        // calificaciones, unidades o materiales. Esas entidades aún no existen.
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
        // TODO M5: si el curso ya tiene asistencias, la hoja no se puede
        // reemplazar (409). La entidad Asistencia aún no existe.

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
