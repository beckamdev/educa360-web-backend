package com.educa360.backend.service;

import com.educa360.backend.dto.ContenidoRequest;
import com.educa360.backend.dto.ContenidoResponse;
import com.educa360.backend.dto.ReordenarContenidosRequest;
import com.educa360.backend.dto.UnidadCreateRequest;
import com.educa360.backend.dto.UnidadResponse;
import com.educa360.backend.dto.UnidadUpdateRequest;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Contenido;
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
import com.educa360.backend.repository.ContenidoRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.UnidadRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Planificación docente: unidades por curso/bimestre y contenidos ordenados.
 *
 * Reglas que gobierna (Módulo 4):
 * - sólo el docente dueño del curso crea, edita, publica o elimina;
 * - el bimestre debe ser del mismo año que la sección del curso;
 * - se planifica en bimestres PLANIFICADO o ABIERTO, nunca CERRADO;
 * - el número de la unidad y el orden del contenido son automáticos;
 * - el alumno y el apoderado sólo ven unidades PUBLICADA;
 * - el reordenamiento se hace en dos pasadas para respetar el unique.
 *
 * Las validaciones de calificaciones (M6) y materiales (M8) quedan como TODO:
 * esas entidades todavía no existen.
 */
@Service
public class PlanificacionService {

    private final UnidadRepository unidadRepository;
    private final ContenidoRepository contenidoRepository;
    private final CursoRepository cursoRepository;
    private final BimestreRepository bimestreRepository;
    private final DocenteRepository docenteRepository;
    private final UserRepository userRepository;
    private final EstudianteRepository estudianteRepository;
    private final MatriculaService matriculaService;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;

    public PlanificacionService(UnidadRepository unidadRepository,
                                ContenidoRepository contenidoRepository,
                                CursoRepository cursoRepository,
                                BimestreRepository bimestreRepository,
                                DocenteRepository docenteRepository,
                                UserRepository userRepository,
                                EstudianteRepository estudianteRepository,
                                MatriculaService matriculaService,
                                ApoderadoRepository apoderadoRepository,
                                ApoderadoEstudianteRepository vinculoRepository) {
        this.unidadRepository = unidadRepository;
        this.contenidoRepository = contenidoRepository;
        this.cursoRepository = cursoRepository;
        this.bimestreRepository = bimestreRepository;
        this.docenteRepository = docenteRepository;
        this.userRepository = userRepository;
        this.estudianteRepository = estudianteRepository;
        this.matriculaService = matriculaService;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
    }

    // ==================== Docente: unidades ====================

    @Transactional(readOnly = true)
    public List<UnidadResponse> listarDelCurso(String email, Long cursoId) {
        Docente docente = docenteDe(email);
        Curso curso = cursoDelDocente(docente, cursoId);
        return unidadRepository.findByCursoIdOrderByBimestreNumeroAscNumeroAsc(curso.getId())
                .stream()
                .map(UnidadResponse::desde)
                .toList();
    }

    @Transactional
    public UnidadResponse crear(String email, Long cursoId, UnidadCreateRequest datos) {
        Docente docente = docenteDe(email);
        Curso curso = cursoDelDocente(docente, cursoId);
        Bimestre bimestre = bimestreRepository.findById(datos.bimestreId())
                .orElseThrow(() -> new NotFoundException(
                        "El bimestre " + datos.bimestreId() + " no existe"));

        validarBimestreDelCurso(curso, bimestre);
        validarAbiertoParaPlanificar(bimestre);
        validarFechasUnidad(datos.fechaInicio(), datos.fechaFin(), bimestre);

        Integer numero = unidadRepository
                .findFirstByCursoIdAndBimestreIdOrderByNumeroDesc(curso.getId(), bimestre.getId())
                .map(ultima -> ultima.getNumero() + 1)
                .orElse(1);

        Unidad unidad = new Unidad(curso, bimestre, numero, datos.titulo());
        unidad.setDescripcion(datos.descripcion());
        unidad.setFechaInicio(datos.fechaInicio());
        unidad.setFechaFin(datos.fechaFin());
        return UnidadResponse.desde(unidadRepository.save(unidad));
    }

    @Transactional(readOnly = true)
    public UnidadResponse buscar(String email, Long unidadId) {
        Docente docente = docenteDe(email);
        return UnidadResponse.desde(unidadDelDocente(docente, unidadId));
    }

    @Transactional
    public UnidadResponse actualizar(String email, Long unidadId, UnidadUpdateRequest datos) {
        Docente docente = docenteDe(email);
        Unidad unidad = unidadDelDocente(docente, unidadId);

        validarAbiertoParaPlanificar(unidad.getBimestre());
        validarFechasUnidad(datos.fechaInicio(), datos.fechaFin(), unidad.getBimestre());

        unidad.setTitulo(datos.titulo());
        unidad.setDescripcion(datos.descripcion());
        unidad.setFechaInicio(datos.fechaInicio());
        unidad.setFechaFin(datos.fechaFin());

        // Si la unidad ya tenía contenidos, sus fechas previstas deben seguir
        // cayendo dentro del nuevo rango.
        unidad.getContenidos().forEach(contenido ->
                validarFechaContenido(contenido.getFechaPrevista(), unidad));

        return UnidadResponse.desde(unidadRepository.save(unidad));
    }

    @Transactional
    public UnidadResponse cambiarEstado(String email, Long unidadId, Unidad.Estado estado) {
        if (estado == null) {
            throw new BadRequestException("El estado es obligatorio");
        }
        Docente docente = docenteDe(email);
        Unidad unidad = unidadDelDocente(docente, unidadId);

        validarAbiertoParaPlanificar(unidad.getBimestre());
        // TODO M6: despublicar sólo si la unidad no tiene calificaciones.
        unidad.setEstado(estado);
        return UnidadResponse.desde(unidadRepository.save(unidad));
    }

    @Transactional
    public void eliminar(String email, Long unidadId) {
        Docente docente = docenteDe(email);
        Unidad unidad = unidadDelDocente(docente, unidadId);

        validarAbiertoParaPlanificar(unidad.getBimestre());
        // TODO M6/M8: bloquear si tiene calificaciones o contenidos con materiales.
        // Hoy sus contenidos se eliminan en cascada (no tienen materiales).
        unidadRepository.delete(unidad);
    }

    // ==================== Docente: contenidos ====================

    @Transactional(readOnly = true)
    public List<ContenidoResponse> listarContenidos(String email, Long unidadId) {
        Docente docente = docenteDe(email);
        Unidad unidad = unidadDelDocente(docente, unidadId);
        return contenidoRepository.findByUnidadIdOrderByOrdenAsc(unidad.getId()).stream()
                .map(ContenidoResponse::desde)
                .toList();
    }

    @Transactional
    public ContenidoResponse crearContenido(String email, Long unidadId, ContenidoRequest datos) {
        Docente docente = docenteDe(email);
        Unidad unidad = unidadDelDocente(docente, unidadId);

        validarAbiertoParaPlanificar(unidad.getBimestre());
        validarFechaContenido(datos.fechaPrevista(), unidad);

        Integer orden = contenidoRepository
                .findFirstByUnidadIdOrderByOrdenDesc(unidad.getId())
                .map(ultimo -> ultimo.getOrden() + 1)
                .orElse(1);

        Contenido contenido = new Contenido(unidad, orden, datos.titulo());
        contenido.setDescripcion(datos.descripcion());
        contenido.setFechaPrevista(datos.fechaPrevista());
        return ContenidoResponse.desde(contenidoRepository.save(contenido));
    }

    @Transactional
    public ContenidoResponse actualizarContenido(String email, Long contenidoId, ContenidoRequest datos) {
        Docente docente = docenteDe(email);
        Contenido contenido = contenidoDelDocente(docente, contenidoId);

        validarAbiertoParaPlanificar(contenido.getUnidad().getBimestre());
        validarFechaContenido(datos.fechaPrevista(), contenido.getUnidad());

        contenido.setTitulo(datos.titulo());
        contenido.setDescripcion(datos.descripcion());
        contenido.setFechaPrevista(datos.fechaPrevista());
        return ContenidoResponse.desde(contenidoRepository.save(contenido));
    }

    @Transactional
    public void eliminarContenido(String email, Long contenidoId) {
        Docente docente = docenteDe(email);
        Contenido contenido = contenidoDelDocente(docente, contenidoId);

        validarAbiertoParaPlanificar(contenido.getUnidad().getBimestre());
        // TODO M8: bloquear si el contenido tiene materiales.
        contenidoRepository.delete(contenido);
    }

    /**
     * Reordena la unidad completa. La lista debe traer exactamente los ids
     * actuales. Primero se pasan todos a órdenes temporales negativos y se
     * hace flush; después se asignan 1..n. Así el unique nunca se viola.
     */
    @Transactional
    public List<ContenidoResponse> reordenar(String email, Long unidadId,
                                             ReordenarContenidosRequest datos) {
        Docente docente = docenteDe(email);
        Unidad unidad = unidadDelDocente(docente, unidadId);
        validarAbiertoParaPlanificar(unidad.getBimestre());

        List<Long> ids = datos.contenidoIds();
        if (ids == null) {
            throw new BadRequestException("La lista de contenidos es obligatoria");
        }
        if (ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BadRequestException("Ningún contenido puede ser nulo");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BadRequestException("La lista no puede repetir contenidos");
        }

        List<Contenido> actuales =
                contenidoRepository.findByUnidadIdOrderByOrdenAsc(unidad.getId());
        Set<Long> actualesIds = actuales.stream()
                .map(Contenido::getId)
                .collect(Collectors.toSet());
        if (ids.size() != actuales.size() || !actualesIds.equals(new HashSet<>(ids))) {
            throw new BadRequestException(
                    "La lista debe incluir exactamente los contenidos de la unidad");
        }

        for (int i = 0; i < actuales.size(); i++) {
            actuales.get(i).setOrden(-(i + 1));
        }
        contenidoRepository.flush();

        Map<Long, Contenido> porId = actuales.stream()
                .collect(Collectors.toMap(Contenido::getId, contenido -> contenido));
        for (int i = 0; i < ids.size(); i++) {
            porId.get(ids.get(i)).setOrden(i + 1);
        }
        contenidoRepository.flush();

        return actuales.stream()
                .sorted(Comparator.comparing(Contenido::getOrden))
                .map(ContenidoResponse::desde)
                .toList();
    }

    // ==================== Estudiante y apoderado: sólo publicadas ====================

    @Transactional(readOnly = true)
    public List<UnidadResponse> listarDelEstudiante(String email, Long cursoId) {
        Estudiante estudiante = estudianteDe(email);
        Matricula matricula = matriculaVigente(estudiante);
        Curso curso = cursoDeLaSeccion(matricula, cursoId);
        return publicadasDe(curso);
    }

    @Transactional(readOnly = true)
    public List<UnidadResponse> listarDelApoderado(String email, Long hijoId, Long cursoId) {
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
        Matricula matricula = matriculaVigente(estudiante);
        Curso curso = cursoDeLaSeccion(matricula, cursoId);
        return publicadasDe(curso);
    }

    // ==================== ADMIN: sólo lectura ====================

    @Transactional(readOnly = true)
    public List<UnidadResponse> listarDelCursoAdmin(Long cursoId) {
        cursoRepository.findById(cursoId)
                .orElseThrow(() -> new NotFoundException("El curso " + cursoId + " no existe"));
        return unidadRepository.findByCursoIdOrderByBimestreNumeroAscNumeroAsc(cursoId).stream()
                .map(UnidadResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnidadResponse buscarAdmin(Long unidadId) {
        return UnidadResponse.desde(unidadRepository.findById(unidadId)
                .orElseThrow(() -> new NotFoundException("La unidad " + unidadId + " no existe")));
    }

    // ==================== Helpers ====================

    private List<UnidadResponse> publicadasDe(Curso curso) {
        return unidadRepository
                .findByCursoIdAndEstadoOrderByBimestreNumeroAscNumeroAsc(
                        curso.getId(), Unidad.Estado.PUBLICADA)
                .stream()
                .map(UnidadResponse::desde)
                .toList();
    }

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
            // 404 a propósito: no revelar que el curso existe pero es ajeno.
            throw new NotFoundException("El curso " + cursoId + " no existe");
        }
        return curso;
    }

    private Unidad unidadDelDocente(Docente docente, Long unidadId) {
        Unidad unidad = unidadRepository.findById(unidadId)
                .orElseThrow(() -> new NotFoundException("La unidad " + unidadId + " no existe"));
        if (!unidad.getCurso().getDocente().getId().equals(docente.getId())) {
            throw new NotFoundException("La unidad " + unidadId + " no existe");
        }
        return unidad;
    }

    private Contenido contenidoDelDocente(Docente docente, Long contenidoId) {
        Contenido contenido = contenidoRepository.findById(contenidoId)
                .orElseThrow(() -> new NotFoundException(
                        "El contenido " + contenidoId + " no existe"));
        if (!contenido.getUnidad().getCurso().getDocente().getId().equals(docente.getId())) {
            throw new NotFoundException("El contenido " + contenidoId + " no existe");
        }
        return contenido;
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

    private Matricula matriculaVigente(Estudiante estudiante) {
        return matriculaService.vigenteDe(estudiante.getId())
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante no tiene matrícula en el año vigente"));
    }

    private Curso cursoDeLaSeccion(Matricula matricula, Long cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new NotFoundException("El curso " + cursoId + " no existe"));
        if (!curso.getSeccion().getId().equals(matricula.getSeccion().getId())) {
            throw new NotFoundException("El curso " + cursoId + " no existe");
        }
        return curso;
    }

    private void validarBimestreDelCurso(Curso curso, Bimestre bimestre) {
        if (!bimestre.getAnio().getId().equals(curso.getSeccion().getAnio().getId())) {
            throw new ConflictException("El " + bimestre.getNombre()
                    + " no pertenece al año escolar del curso");
        }
    }

    private void validarAbiertoParaPlanificar(Bimestre bimestre) {
        if (bimestre.estaCerrado()) {
            throw new ConflictException("El " + bimestre.getNombre()
                    + " está CERRADO: no se puede modificar la planificación");
        }
        if (bimestre.getAnio().estaCerrado()) {
            throw new ConflictException("El año " + bimestre.getAnio().getAnio()
                    + " está CERRADO: no se puede modificar la planificación");
        }
    }

    private void validarFechasUnidad(LocalDate inicio, LocalDate fin, Bimestre bimestre) {
        if (inicio != null && (inicio.isBefore(bimestre.getFechaInicio())
                || inicio.isAfter(bimestre.getFechaFin()))) {
            throw new BadRequestException("La fecha de inicio de la unidad debe estar dentro del "
                    + bimestre.getNombre());
        }
        if (fin != null && (fin.isBefore(bimestre.getFechaInicio())
                || fin.isAfter(bimestre.getFechaFin()))) {
            throw new BadRequestException("La fecha de fin de la unidad debe estar dentro del "
                    + bimestre.getNombre());
        }
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new BadRequestException(
                    "La fecha de fin de la unidad no puede ser anterior a la de inicio");
        }
    }

    private void validarFechaContenido(LocalDate fecha, Unidad unidad) {
        if (fecha == null) {
            return;
        }
        Bimestre bimestre = unidad.getBimestre();
        if (fecha.isBefore(bimestre.getFechaInicio()) || fecha.isAfter(bimestre.getFechaFin())) {
            throw new BadRequestException("La fecha prevista del contenido debe estar dentro del "
                    + bimestre.getNombre());
        }
        if (unidad.getFechaInicio() != null && fecha.isBefore(unidad.getFechaInicio())) {
            throw new BadRequestException(
                    "La fecha prevista del contenido no puede ser anterior al inicio de la unidad");
        }
        if (unidad.getFechaFin() != null && fecha.isAfter(unidad.getFechaFin())) {
            throw new BadRequestException(
                    "La fecha prevista del contenido no puede ser posterior al fin de la unidad");
        }
    }
}
