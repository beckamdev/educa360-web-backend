package com.educa360.backend.service;

import com.educa360.backend.dto.ApoderadoResponse;
import com.educa360.backend.dto.ApoderadoRequest;
import com.educa360.backend.dto.HijoResponse;
import com.educa360.backend.dto.RepresentadoRequest;
import com.educa360.backend.dto.UsuarioBasicoRequest;
import com.educa360.backend.dto.UsuarioResponse;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Telefono;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Alta del perfil APODERADO y reglas de representación (Módulo 3).
 *
 * Reglas de los vínculos:
 * - quien los CREA/MODIFICA es Secretaría (§5: el apoderado sólo CONSULTA
 *   sus representados, no se los asigna a sí mismo);
 * - baja lógica (activo=false): se guarda el historial de la familia;
 * - un SOLO responsable principal por estudiante: al nombrar uno nuevo,
 *   el anterior deja de serlo;
 * - re-vincular un par dado de baja lo reactiva (upsert con el parentesco).
 */
@Service
public class ApoderadoService {

    private final UsuarioService usuarioService;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final EstudianteRepository estudianteRepository;
    private final UserRepository userRepository;
    private final MatriculaService matriculaService;

    public ApoderadoService(UsuarioService usuarioService,
                            ApoderadoRepository apoderadoRepository,
                            ApoderadoEstudianteRepository vinculoRepository,
                            EstudianteRepository estudianteRepository,
                            UserRepository userRepository,
                            MatriculaService matriculaService) {
        this.usuarioService = usuarioService;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
        this.estudianteRepository = estudianteRepository;
        this.userRepository = userRepository;
        this.matriculaService = matriculaService;
    }

    /** Crea la cuenta y su perfil Apoderado en la misma transacción. */
    @Transactional
    public UsuarioResponse crear(UsuarioBasicoRequest datos, ApoderadoRequest perfil) {
        User user = usuarioService.crearCuenta(datos, User.Role.APODERADO);
        apoderadoRepository.save(new Apoderado(user));
        return usuarioService.aRespuesta(user);
    }

    // ==================== Representados (gestión de Secretaría) ====================

    /** Vincula estudiante ↔ apoderado (o reactiva el par si estaba de baja). */
    @Transactional
    public HijoResponse vincular(Long apoderadoId, RepresentadoRequest datos) {
        Apoderado apoderado = buscar(apoderadoId);
        Estudiante estudiante = estudianteRepository.findById(datos.estudianteId())
                .orElseThrow(() -> new NotFoundException("El estudiante " + datos.estudianteId() + " no existe"));

        if (datos.responsablePrincipal()) {
            // Un solo responsable principal: al nombrar uno nuevo, el anterior deja de serlo.
            vinculoRepository.findByEstudianteIdAndActivoTrue(estudiante.getId())
                    .forEach(vinculo -> vinculo.setResponsablePrincipal(false));
        }

        ApoderadoEstudiante vinculo = vinculoRepository
                .findByApoderadoIdAndEstudianteId(apoderadoId, estudiante.getId())
                .orElseGet(() -> new ApoderadoEstudiante(apoderado, estudiante,
                        datos.parentesco(), datos.responsablePrincipal()));
        vinculo.setParentesco(datos.parentesco());
        vinculo.setResponsablePrincipal(datos.responsablePrincipal());
        vinculo.setActivo(true); // un par dado de baja se reactiva en vez de duplicarse
        return aHijo(vinculoRepository.save(vinculo));
    }

    /** Baja lógica del vínculo: el apoderado deja de ver al hijo (queda el historial). */
    @Transactional
    public void desvincular(Long apoderadoId, Long estudianteId) {
        ApoderadoEstudiante vinculo = vinculoRepository
                .findByApoderadoIdAndEstudianteId(apoderadoId, estudianteId)
                .filter(ApoderadoEstudiante::isActivo)
                .orElseThrow(() -> new NotFoundException(
                        "Ese estudiante no está vinculado a este apoderado"));
        vinculo.setActivo(false);
    }

    // ==================== Consultas ====================

    /** Los hijos VIVOS de un apoderado, con su sección del período vigente. */
    @Transactional(readOnly = true)
    public List<HijoResponse> listarHijos(Long apoderadoId) {
        buscar(apoderadoId); // 404 si el perfil no existe
        return vinculoRepository.findByApoderadoIdAndActivoTrue(apoderadoId).stream()
                .map(this::aHijo)
                .toList();
    }

    /** Igual, resuelto por el token (zona /apoderado: el dueño sale del JWT). */
    @Transactional(readOnly = true)
    public List<HijoResponse> listarHijosDelUsuario(String email) {
        return apoderadoDe(email).map(apoderado -> listarHijos(apoderado.getId())).orElse(List.of());
    }

    /** Listado de familias para Secretaría (?q= para buscar). Escala de un colegio: se filtra en memoria. */
    @Transactional(readOnly = true)
    public List<ApoderadoResponse> listar(String q) {
        Map<Long, List<ApoderadoEstudiante>> porApoderado = vinculoRepository.findAll().stream()
                .filter(ApoderadoEstudiante::isActivo)
                .collect(Collectors.groupingBy(vinculo -> vinculo.getApoderado().getId()));

        return apoderadoRepository.findAll().stream()
                .filter(apoderado -> q == null || q.isBlank() || coincide(apoderado.getUser(), q))
                .map(apoderado -> aRespuesta(apoderado,
                        porApoderado.getOrDefault(apoderado.getId(), List.of())))
                .toList();
    }

    // ==================== Helpers ====================

    private Apoderado buscar(Long id) {
        return apoderadoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("El apoderado " + id + " no existe"));
    }

    private Optional<Apoderado> apoderadoDe(String email) {
        return userRepository.findByEmail(email).flatMap(apoderadoRepository::findByUser);
    }

    private boolean coincide(User user, String q) {
        String texto = q.toLowerCase();
        return (user.getEmail() != null && user.getEmail().toLowerCase().contains(texto))
                || (user.getNombres() != null && user.getNombres().toLowerCase().contains(texto))
                || (user.getApellidos() != null && user.getApellidos().toLowerCase().contains(texto))
                || (user.getDni() != null && user.getDni().contains(q));
    }

    /** Hijo con su sección (matrícula del período ABIERTO; null si aún no está matriculado). */
    private HijoResponse aHijo(ApoderadoEstudiante vinculo) {
        Estudiante estudiante = vinculo.getEstudiante();
        Optional<Matricula> vigente = matriculaService.vigenteDe(estudiante.getId());
        return new HijoResponse(
                estudiante.getId(),
                estudiante.getUser().getNombres(),
                estudiante.getUser().getApellidos(),
                estudiante.getUser().getDni(),
                estudiante.getUser().getEmail(),
                vigente.map(matricula -> matricula.getSeccion().getEtiqueta()).orElse(null),
                vigente.map(matricula -> matricula.getSeccion().getAnio().getNombre()).orElse(null),
                vigente.map(Matricula::getEstado).orElse(null),
                vinculo.getParentesco(),
                vinculo.isResponsablePrincipal()
        );
    }

    private ApoderadoResponse aRespuesta(Apoderado apoderado, List<ApoderadoEstudiante> vinculos) {
        User user = apoderado.getUser();
        return new ApoderadoResponse(
                apoderado.getId(),
                user.getEmail(),
                user.getNombres(),
                user.getApellidos(),
                user.getDni(),
                user.getTelefonos().stream().map(Telefono::getNumero).sorted().toList(),
                user.isActivo(),
                vinculos.stream()
                        .map(vinculo -> new ApoderadoResponse.HijoBasico(
                                vinculo.getEstudiante().getId(),
                                vinculo.getEstudiante().getUser().getNombres() + " "
                                        + vinculo.getEstudiante().getUser().getApellidos(),
                                vinculo.getParentesco(),
                                vinculo.isResponsablePrincipal()))
                        .toList()
        );
    }
}
