package com.educa360.backend.service;

import com.educa360.backend.dto.BoletaBimestreResponse;
import com.educa360.backend.dto.BoletaCursoResponse;
import com.educa360.backend.dto.BoletaNotaBimestreResponse;
import com.educa360.backend.dto.BoletaResponse;
import com.educa360.backend.dto.CierreAlumnoResponse;
import com.educa360.backend.dto.CierreCursoResponse;
import com.educa360.backend.dto.CierreResponse;
import com.educa360.backend.dto.HistorialAnioResponse;
import com.educa360.backend.dto.HistorialEstudianteResponse;
import com.educa360.backend.dto.ReporteAsistenciaFilaResponse;
import com.educa360.backend.dto.ReporteAsistenciaResponse;
import com.educa360.backend.dto.ReporteCalificacionFilaResponse;
import com.educa360.backend.dto.ReporteCalificacionesResponse;
import com.educa360.backend.dto.ReporteCursoPromedioResponse;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Asistencia;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Calificacion;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.AsistenciaRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.CalificacionRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.SeccionRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Cierre anual y reportes (Módulo 10).
 *
 * Reglas:
 * - sólo se cierra un año con todos sus bimestres CERRADOS;
 * - si a un alumno activo le falta el promedio anual de algún curso, el cierre
 *   se bloquea y ADMIN debe revisar;
 * - APROBADO exige TODOS los cursos con promedio anual ≥ 11.00 y asistencia
 *   global ≥ 70%; un solo curso desaprobado impide APROBADO;
 * - RETIRADO se conserva y no bloquea por notas;
 * - al aprobar 5° grado, el estudiante pasa a EGRESADO;
 * - el cierre es idempotente: repetirlo no duplica efectos;
 * - boletas e historial se calculan, no se guardan;
 * - los cálculos usan sólo unidades PUBLICADA, igual que la vista del alumno.
 */
@Service
public class CierreService {

    private static final BigDecimal NOTA_APROBATORIA = new BigDecimal("11.00");
    private static final BigDecimal ASISTENCIA_MINIMA = new BigDecimal("70.00");

    private final AnioEscolarRepository anioRepository;
    private final BimestreRepository bimestreRepository;
    private final SeccionRepository seccionRepository;
    private final MatriculaRepository matriculaRepository;
    private final CursoRepository cursoRepository;
    private final CalificacionRepository calificacionRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EstudianteRepository estudianteRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final UserRepository userRepository;
    private final MatriculaService matriculaService;
    private final NotificacionService notificacionService;

    public CierreService(AnioEscolarRepository anioRepository,
                         BimestreRepository bimestreRepository,
                         SeccionRepository seccionRepository,
                         MatriculaRepository matriculaRepository,
                         CursoRepository cursoRepository,
                         CalificacionRepository calificacionRepository,
                         AsistenciaRepository asistenciaRepository,
                         EstudianteRepository estudianteRepository,
                         ApoderadoRepository apoderadoRepository,
                         ApoderadoEstudianteRepository vinculoRepository,
                         UserRepository userRepository,
                         MatriculaService matriculaService,
                         NotificacionService notificacionService) {
        this.anioRepository = anioRepository;
        this.bimestreRepository = bimestreRepository;
        this.seccionRepository = seccionRepository;
        this.matriculaRepository = matriculaRepository;
        this.cursoRepository = cursoRepository;
        this.calificacionRepository = calificacionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.estudianteRepository = estudianteRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
        this.userRepository = userRepository;
        this.matriculaService = matriculaService;
        this.notificacionService = notificacionService;
    }

    // ==================== Cierre anual ====================

    @Transactional(readOnly = true)
    public CierreResponse preview(Long anioId) {
        return calcularCierre(anioDe(anioId));
    }

    @Transactional
    public CierreResponse cerrar(Long anioId) {
        AnioEscolar anio = anioDe(anioId);

        // Idempotente: un año ya cerrado no vuelve a aplicar efectos.
        if (anio.estaCerrado()) {
            return calcularCierre(anio);
        }

        CierreResponse preview = calcularCierre(anio);
        if (!preview.listo()) {
            throw new ConflictException("No se puede cerrar el año: "
                    + String.join("; ", preview.bloqueos()));
        }

        for (CierreAlumnoResponse alumno : preview.alumnos()) {
            if (alumno.situacionPropuesta() == null
                    || alumno.situacionActual() == Matricula.SituacionFinal.RETIRADO) {
                continue;
            }
            Matricula matricula = matriculaRepository.findById(alumno.matriculaId())
                    .orElseThrow(() -> new NotFoundException(
                            "La matrícula " + alumno.matriculaId() + " no existe"));
            if (matricula.estaRetirada()) {
                continue;
            }

            matricula.setSituacionFinal(alumno.situacionPropuesta());
            if (alumno.situacionPropuesta() == Matricula.SituacionFinal.APROBADO
                    && matricula.getSeccion().getGrado() == 5) {
                matricula.getEstudiante().setEstado(Estudiante.EstadoEstudiante.EGRESADO);
            }
            matriculaRepository.save(matricula);
        }

        anio.setEstado(AnioEscolar.Estado.CERRADO);
        anioRepository.save(anio);
        notificacionService.notificarCierre(anio);
        return calcularCierre(anio);
    }

    private CierreResponse calcularCierre(AnioEscolar anio) {
        List<Bimestre> bimestres = bimestresDe(anio);
        List<String> bloqueos = new ArrayList<>();

        if (bimestres.isEmpty()) {
            bloqueos.add("El año no tiene bimestres");
        }
        bimestres.stream()
                .filter(bimestre -> bimestre.getEstado() != Bimestre.Estado.CERRADO)
                .forEach(bimestre -> bloqueos.add(
                        "El " + bimestre.getNombre() + " no está CERRADO"));

        List<CierreAlumnoResponse> alumnos = new ArrayList<>();
        int aprobados = 0;
        int desaprobados = 0;
        int retirados = 0;
        int conNotasFaltantes = 0;

        for (Matricula matricula : matriculasDe(anio, null)) {
            Calculo calculo = calcular(matricula, null);
            AsistenciaCalculo asistencia = calcularAsistencia(matricula, null);

            boolean faltanNotas = !matricula.estaRetirada()
                    && calculo.cursos().stream()
                            .anyMatch(curso -> curso.promedioAnual() == null);

            Matricula.SituacionFinal propuesta;
            if (matricula.estaRetirada()) {
                propuesta = Matricula.SituacionFinal.RETIRADO;
                retirados++;
            } else if (faltanNotas) {
                propuesta = null;
                conNotasFaltantes++;
                calculo.cursos().stream()
                        .filter(curso -> curso.promedioAnual() == null)
                        .forEach(curso -> bloqueos.add("Faltan notas de "
                                + curso.curso().getMateria().getNombre()
                                + " para " + nombreDe(matricula)));
            } else if (calculo.cursos().stream()
                    .anyMatch(curso -> curso.promedioAnual()
                            .compareTo(NOTA_APROBATORIA) < 0)
                    || asistencia.porcentaje().compareTo(ASISTENCIA_MINIMA) < 0) {
                propuesta = Matricula.SituacionFinal.DESAPROBADO;
                desaprobados++;
            } else {
                propuesta = Matricula.SituacionFinal.APROBADO;
                aprobados++;
            }

            List<CierreCursoResponse> cursos = calculo.cursos().stream()
                    .map(curso -> new CierreCursoResponse(
                            curso.curso().getId(),
                            curso.curso().getMateria().getNombre(),
                            curso.promedioAnual(),
                            curso.promedioAnual() != null
                                    && curso.promedioAnual().compareTo(NOTA_APROBATORIA) >= 0,
                            curso.promedioAnual() != null))
                    .toList();

            User user = matricula.getEstudiante().getUser();
            alumnos.add(new CierreAlumnoResponse(
                    matricula.getId(),
                    matricula.getEstudiante().getId(),
                    user.getNombres() + " " + user.getApellidos(),
                    user.getDni(),
                    matricula.getSeccion().getId(),
                    matricula.getSeccion().getEtiqueta(),
                    matricula.getSeccion().getGrado(),
                    matricula.getSituacionFinal(),
                    propuesta,
                    faltanNotas,
                    calculo.promedioGeneral(),
                    asistencia.porcentaje(),
                    cursos
            ));
        }

        int bimestresCerrados = (int) bimestres.stream()
                .filter(bimestre -> bimestre.getEstado() == Bimestre.Estado.CERRADO)
                .count();

        return new CierreResponse(
                anio.getId(),
                anio.getAnio(),
                anio.getNombre(),
                anio.getEstado(),
                bloqueos.isEmpty(),
                bimestres.size(),
                bimestresCerrados,
                aprobados,
                desaprobados,
                retirados,
                conNotasFaltantes,
                bloqueos,
                alumnos
        );
    }

    // ==================== Boleta e historial ====================

    @Transactional(readOnly = true)
    public BoletaResponse boletaDelEstudiante(String email, Long anioId) {
        Estudiante estudiante = estudianteDe(email);
        return boletaDe(matriculaDe(estudiante, anioId));
    }

    @Transactional(readOnly = true)
    public BoletaResponse boletaDelHijo(String email, Long hijoId, Long anioId) {
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
        return boletaDe(matriculaDe(estudiante, anioId));
    }

    @Transactional(readOnly = true)
    public HistorialEstudianteResponse historial(Long estudianteId) {
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante " + estudianteId + " no existe"));

        List<HistorialAnioResponse> anios = matriculaRepository
                .findByEstudianteId(estudianteId).stream()
                .sorted(Comparator.comparing(
                        (Matricula matricula) -> matricula.getAnio().getAnio()).reversed())
                .map(matricula -> {
                    Calculo calculo = calcular(matricula, null);
                    AsistenciaCalculo asistencia = calcularAsistencia(matricula, null);
                    return new HistorialAnioResponse(
                            matricula.getId(),
                            matricula.getAnio().getId(),
                            matricula.getAnio().getAnio(),
                            matricula.getAnio().getNombre(),
                            matricula.getSeccion().getId(),
                            matricula.getSeccion().getEtiqueta(),
                            matricula.getSeccion().getGrado(),
                            matricula.getSituacionFinal(),
                            calculo.promedioGeneral(),
                            asistencia.porcentaje(),
                            asistencia.total(),
                            asistencia.efectivas()
                    );
                })
                .toList();

        User user = estudiante.getUser();
        return new HistorialEstudianteResponse(
                estudiante.getId(),
                user.getNombres() + " " + user.getApellidos(),
                user.getDni(),
                estudiante.getEstado(),
                anios
        );
    }

    private BoletaResponse boletaDe(Matricula matricula) {
        List<Bimestre> bimestres = bimestresDe(matricula.getAnio());
        Calculo calculo = calcular(matricula, null);
        AsistenciaCalculo asistencia = calcularAsistencia(matricula, null);

        List<BoletaBimestreResponse> columnas = bimestres.stream()
                .map(bimestre -> new BoletaBimestreResponse(
                        bimestre.getId(), bimestre.getNumero(), bimestre.getNombre()))
                .toList();

        List<BoletaCursoResponse> cursos = calculo.cursos().stream()
                .map(curso -> new BoletaCursoResponse(
                        curso.curso().getId(),
                        curso.curso().getMateria().getNombre(),
                        curso.promedioAnual(),
                        curso.promedioAnual() != null
                                && curso.promedioAnual().compareTo(NOTA_APROBATORIA) >= 0,
                        curso.bimestres().stream()
                                .map(bimestre -> new BoletaNotaBimestreResponse(
                                        bimestre.bimestre().getId(),
                                        bimestre.bimestre().getNumero(),
                                        bimestre.bimestre().getNombre(),
                                        bimestre.promedio()))
                                .toList()))
                .toList();

        User user = matricula.getEstudiante().getUser();
        return new BoletaResponse(
                matricula.getId(),
                matricula.getEstudiante().getId(),
                user.getNombres() + " " + user.getApellidos(),
                user.getDni(),
                matricula.getAnio().getId(),
                matricula.getAnio().getAnio(),
                matricula.getAnio().getNombre(),
                matricula.getAnio().getEstado(),
                matricula.getSeccion().getId(),
                matricula.getSeccion().getEtiqueta(),
                matricula.getSeccion().getGrado(),
                matricula.getSituacionFinal(),
                calculo.promedioGeneral(),
                asistencia.porcentaje(),
                asistencia.total(),
                asistencia.efectivas(),
                asistencia.faltas(),
                columnas,
                cursos
        );
    }

    // ==================== Reportes ====================

    @Transactional(readOnly = true)
    public ReporteAsistenciaResponse reporteAsistencia(Long anioId, Long bimestreId,
                                                       Long seccionId) {
        AnioEscolar anio = anioObligatorio(anioId);
        Bimestre bimestre = bimestreId != null ? bimestreDelAnio(bimestreId, anio) : null;
        Seccion seccion = seccionId != null ? seccionDelAnio(seccionId, anio) : null;

        List<ReporteAsistenciaFilaResponse> filas = new ArrayList<>();
        for (Matricula matricula : matriculasDe(anio, seccionId)) {
            AsistenciaCalculo asistencia = calcularAsistencia(matricula, bimestre);
            User user = matricula.getEstudiante().getUser();
            filas.add(new ReporteAsistenciaFilaResponse(
                    matricula.getId(),
                    matricula.getEstudiante().getId(),
                    user.getNombres() + " " + user.getApellidos(),
                    user.getDni(),
                    matricula.getSeccion().getEtiqueta(),
                    asistencia.total(),
                    asistencia.presentes(),
                    asistencia.tardanzas(),
                    asistencia.justificadas(),
                    asistencia.faltas(),
                    asistencia.efectivas(),
                    asistencia.porcentaje(),
                    matricula.getSituacionFinal()
            ));
        }

        return new ReporteAsistenciaResponse(
                anio.getId(),
                anio.getAnio(),
                bimestre != null ? bimestre.getId() : null,
                bimestre != null ? bimestre.getNombre() : null,
                seccion != null ? seccion.getId() : null,
                seccion != null ? seccion.getEtiqueta() : null,
                filas
        );
    }

    @Transactional(readOnly = true)
    public ReporteCalificacionesResponse reporteCalificaciones(Long anioId, Long bimestreId,
                                                               Long seccionId) {
        AnioEscolar anio = anioObligatorio(anioId);
        Bimestre bimestre = bimestreId != null ? bimestreDelAnio(bimestreId, anio) : null;
        Seccion seccion = seccionId != null ? seccionDelAnio(seccionId, anio) : null;

        List<ReporteCalificacionFilaResponse> filas = new ArrayList<>();
        for (Matricula matricula : matriculasDe(anio, seccionId)) {
            Calculo calculo = calcular(matricula, bimestre != null ? bimestre.getId() : null);
            User user = matricula.getEstudiante().getUser();
            List<ReporteCursoPromedioResponse> cursos = calculo.cursos().stream()
                    .map(curso -> new ReporteCursoPromedioResponse(
                            curso.curso().getId(),
                            curso.curso().getMateria().getNombre(),
                            curso.promedioAnual(),
                            curso.promedioAnual() != null
                                    && curso.promedioAnual().compareTo(NOTA_APROBATORIA) >= 0))
                    .toList();
            filas.add(new ReporteCalificacionFilaResponse(
                    matricula.getId(),
                    matricula.getEstudiante().getId(),
                    user.getNombres() + " " + user.getApellidos(),
                    user.getDni(),
                    matricula.getSeccion().getEtiqueta(),
                    cursos,
                    calculo.promedioGeneral(),
                    matricula.getSituacionFinal()
            ));
        }

        return new ReporteCalificacionesResponse(
                anio.getId(),
                anio.getAnio(),
                bimestre != null ? bimestre.getId() : null,
                bimestre != null ? bimestre.getNombre() : null,
                seccion != null ? seccion.getId() : null,
                seccion != null ? seccion.getEtiqueta() : null,
                filas
        );
    }

    // ==================== Cálculo académico ====================

    private Calculo calcular(Matricula matricula, Long bimestreId) {
        AnioEscolar anio = matricula.getAnio();
        List<Bimestre> bimestres = bimestreId == null
                ? bimestresDe(anio)
                : List.of(bimestreDelAnio(bimestreId, anio));

        List<Curso> cursos = cursoRepository.findBySeccionId(matricula.getSeccion().getId())
                .stream()
                .sorted(Comparator.comparing(
                        curso -> curso.getMateria().getNombre(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<Calificacion> notas = calificacionRepository
                .findByMatriculaIdOrderByUnidadBimestreNumeroAscUnidadNumeroAscCriterioAsc(
                        matricula.getId())
                .stream()
                .filter(nota -> nota.getUnidad().estaPublicada())
                .filter(nota -> bimestreId == null
                        || nota.getUnidad().getBimestre().getId().equals(bimestreId))
                .toList();

        List<CursoCalculado> cursosCalculados = new ArrayList<>();
        for (Curso curso : cursos) {
            Map<Long, List<Calificacion>> porBimestre = notas.stream()
                    .filter(nota -> nota.getUnidad().getCurso().getId().equals(curso.getId()))
                    .collect(Collectors.groupingBy(
                            nota -> nota.getUnidad().getBimestre().getId()));

            List<BimestreCalculado> promediosBimestre = bimestres.stream()
                    .map(bimestre -> new BimestreCalculado(
                            bimestre,
                            promedioPorUnidades(
                                    porBimestre.getOrDefault(bimestre.getId(), List.of()))))
                    .toList();

            BigDecimal promedioAnual = promedio(promediosBimestre.stream()
                    .map(BimestreCalculado::promedio)
                    .toList());

            cursosCalculados.add(new CursoCalculado(
                    curso, promediosBimestre, promedioAnual));
        }

        BigDecimal promedioGeneral = promedio(cursosCalculados.stream()
                .map(CursoCalculado::promedioAnual)
                .toList());

        return new Calculo(cursosCalculados, promedioGeneral);
    }

    /** Promedio del bimestre por curso: media simple de las unidades con notas. */
    private BigDecimal promedioPorUnidades(List<Calificacion> notas) {
        Map<Long, List<Calificacion>> porUnidad = notas.stream()
                .collect(Collectors.groupingBy(
                        nota -> nota.getUnidad().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<BigDecimal> promediosUnidad = porUnidad.values().stream()
                .map(lista -> promedio(lista.stream()
                        .map(Calificacion::getNota)
                        .toList()))
                .toList();

        return promedio(promediosUnidad);
    }

    private BigDecimal promedio(List<BigDecimal> valores) {
        List<BigDecimal> limpios = valores.stream()
                .filter(Objects::nonNull)
                .toList();
        if (limpios.isEmpty()) {
            return null;
        }
        BigDecimal suma = limpios.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(limpios.size()), 2, RoundingMode.HALF_UP);
    }

    // ==================== Cálculo de asistencia ====================

    private AsistenciaCalculo calcularAsistencia(Matricula matricula, Bimestre bimestre) {
        List<Asistencia> asistencias = bimestre == null
                ? asistenciaRepository
                        .findByMatriculaIdOrderByFechaAscBloqueHoraInicioAsc(matricula.getId())
                : asistenciaRepository
                        .findByMatriculaIdAndFechaBetweenOrderByFechaAscBloqueHoraInicioAsc(
                                matricula.getId(),
                                bimestre.getFechaInicio(),
                                bimestre.getFechaFin());

        int presentes = contar(asistencias, Asistencia.Estado.PRESENTE);
        int tardanzas = contar(asistencias, Asistencia.Estado.TARDANZA);
        int justificadas = contar(asistencias, Asistencia.Estado.JUSTIFICADA);
        int faltas = contar(asistencias, Asistencia.Estado.FALTA);
        int efectivas = presentes + tardanzas + justificadas;
        int total = asistencias.size();
        BigDecimal porcentaje = total == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(efectivas)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return new AsistenciaCalculo(total, presentes, tardanzas, justificadas,
                faltas, efectivas, porcentaje);
    }

    private int contar(List<Asistencia> asistencias, Asistencia.Estado estado) {
        return (int) asistencias.stream()
                .filter(asistencia -> asistencia.getEstado() == estado)
                .count();
    }

    // ==================== Helpers ====================

    private AnioEscolar anioDe(Long id) {
        return anioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("El año " + id + " no existe"));
    }

    private AnioEscolar anioObligatorio(Long anioId) {
        if (anioId == null) {
            throw new BadRequestException("El año escolar es obligatorio");
        }
        return anioDe(anioId);
    }

    private Bimestre bimestreDelAnio(Long bimestreId, AnioEscolar anio) {
        Bimestre bimestre = bimestreRepository.findById(bimestreId)
                .orElseThrow(() -> new NotFoundException(
                        "El bimestre " + bimestreId + " no existe"));
        if (!bimestre.getAnio().getId().equals(anio.getId())) {
            throw new ConflictException("El " + bimestre.getNombre()
                    + " no pertenece al año " + anio.getAnio());
        }
        return bimestre;
    }

    private Seccion seccionDelAnio(Long seccionId, AnioEscolar anio) {
        Seccion seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> new NotFoundException(
                        "La sección " + seccionId + " no existe"));
        if (!seccion.getAnio().getId().equals(anio.getId())) {
            throw new ConflictException("La sección " + seccion.getEtiqueta()
                    + " no pertenece al año " + anio.getAnio());
        }
        return seccion;
    }

    private List<Bimestre> bimestresDe(AnioEscolar anio) {
        return bimestreRepository.findByAnioIdOrderByNumeroAsc(anio.getId());
    }

    private List<Matricula> matriculasDe(AnioEscolar anio, Long seccionId) {
        List<Matricula> matriculas = seccionId != null
                ? matriculaRepository.findBySeccionId(seccionId)
                : matriculaRepository.findByAnioId(anio.getId());
        return matriculas.stream()
                .sorted(Comparator
                        .comparing((Matricula matricula) ->
                                matricula.getSeccion().getEtiqueta())
                        .thenComparing(this::nombreDe, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Estudiante estudianteDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Estudiante estudiante = estudianteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Estudiante"));
        if (!estudiante.estaHabilitado()) {
            throw new NotFoundException("El estudiante no está activo");
        }
        return estudiante;
    }

    private Apoderado apoderadoDe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return apoderadoRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Apoderado"));
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

    // ==================== Tipos internos ====================

    private record Calculo(List<CursoCalculado> cursos, BigDecimal promedioGeneral) {
    }

    private record CursoCalculado(Curso curso, List<BimestreCalculado> bimestres,
                                  BigDecimal promedioAnual) {
    }

    private record BimestreCalculado(Bimestre bimestre, BigDecimal promedio) {
    }

    private record AsistenciaCalculo(int total, int presentes, int tardanzas,
                                     int justificadas, int faltas, int efectivas,
                                     BigDecimal porcentaje) {
    }
}
