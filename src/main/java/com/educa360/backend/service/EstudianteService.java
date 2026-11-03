package com.educa360.backend.service;

import com.educa360.backend.dto.EstudianteEstadoRequest;
import com.educa360.backend.dto.EstudianteRequest;
import com.educa360.backend.dto.EstudianteResponse;
import com.educa360.backend.dto.EstudianteUpdateRequest;
import com.educa360.backend.dto.UsuarioBasicoRequest;
import com.educa360.backend.dto.UsuarioResponse;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Alta y reglas del perfil ESTUDIANTE + su ficha para Secretaría (Módulo 3).
 *
 * Reglas que gobierna este servicio (no el de usuarios):
 * - su ficha exige fecha de nacimiento (@NotNull en EstudianteRequest);
 * - no registra teléfonos propios: al ser menores de edad, la comunicación
 *   es siempre con su apoderado;
 * - su listado es EstudianteResponse (sección, matrícula y apoderados),
 *   NO UsuarioResponse — esa es la vista de cuentas de ADMIN;
 * - la edición de la ficha es acotada: fecha de nacimiento y estado;
 * - el reingreso (ACTIVO) reactiva la matrícula vigente si estaba RETIRADA.
 */
@Service
public class EstudianteService {

    private final UsuarioService usuarioService;
    private final EstudianteRepository estudianteRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final MatriculaService matriculaService;

    public EstudianteService(UsuarioService usuarioService,
                             EstudianteRepository estudianteRepository,
                             ApoderadoEstudianteRepository vinculoRepository,
                             MatriculaService matriculaService) {
        this.usuarioService = usuarioService;
        this.estudianteRepository = estudianteRepository;
        this.vinculoRepository = vinculoRepository;
        this.matriculaService = matriculaService;
    }

    /** Crea la cuenta y su perfil Estudiante en la misma transacción. */
    @Transactional
    public UsuarioResponse crear(UsuarioBasicoRequest datos, EstudianteRequest perfil) {
        if (datos.telefonos() != null && !datos.telefonos().isEmpty()) {
            throw new BadRequestException(
                    "Un estudiante no registra teléfonos propios: la comunicación es con su apoderado");
        }

        User user = usuarioService.crearCuenta(datos, User.Role.ESTUDIANTE);
        estudianteRepository.save(new Estudiante(user, perfil.fechaNacimiento()));
        return usuarioService.aRespuesta(user);
    }

    /**
     * Listado para Secretaría: ?q= busca en email/nombres/apellidos/dni,
     * ?estado= filtra por estado académico.
     *
     * Se filtra en memoria (a propósito): la escala es la de un colegio y la
     * ficha necesaria ya viene de findAll — una Specification sólo encarecería
     * el código sin ganar nada medible.
     */
    @Transactional(readOnly = true)
    public List<EstudianteResponse> listar(String q, Estudiante.EstadoEstudiante estado) {
        return estudianteRepository.findAll().stream()
                .filter(estudiante -> estado == null || estudiante.getEstado() == estado)
                .filter(estudiante -> q == null || q.isBlank() || coincide(estudiante.getUser(), q))
                .map(this::aRespuesta)
                .toList();
    }

    /** Ficha de un alumno con su matrícula vigente y sus apoderados. */
    @Transactional(readOnly = true)
    public EstudianteResponse buscar(Long id) {
        return aRespuesta(buscarEntidad(id));
    }

    /** Edición acotada: fecha de nacimiento y, opcionalmente, estado académico. */
    @Transactional
    public EstudianteResponse actualizar(Long id, EstudianteUpdateRequest datos) {
        Estudiante estudiante = buscarEntidad(id);
        estudiante.setFechaNacimiento(datos.fechaNacimiento());
        if (datos.estado() != null) {
            aplicarEstado(estudiante, datos.estado());
        }
        return aRespuesta(estudiante);
    }

    /**
     * Cambia el estado académico. ACTIVO es el reingreso: reactiva al alumno
     * y su matrícula vigente si estaba RETIRADA. RETIRADO marca ambas.
     */
    @Transactional
    public EstudianteResponse cambiarEstado(Long id, EstudianteEstadoRequest datos) {
        Estudiante estudiante = buscarEntidad(id);
        aplicarEstado(estudiante, datos.estado());
        return aRespuesta(estudiante);
    }

    // ==================== Helpers ====================

    private void aplicarEstado(Estudiante estudiante, Estudiante.EstadoEstudiante estado) {
        estudiante.setEstado(estado);
        Optional<Matricula> vigente = matriculaService.vigenteDe(estudiante.getId());

        if (estado == Estudiante.EstadoEstudiante.ACTIVO) {
            vigente.filter(Matricula::estaRetirada)
                    .ifPresent(matricula -> matricula.setSituacionFinal(Matricula.SituacionFinal.EN_CURSO));
        } else if (estado == Estudiante.EstadoEstudiante.RETIRADO) {
            vigente.ifPresent(matricula -> matricula.setSituacionFinal(Matricula.SituacionFinal.RETIRADO));
        }
    }

    private Estudiante buscarEntidad(Long id) {
        return estudianteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("El estudiante " + id + " no existe"));
    }

    private boolean coincide(User user, String q) {
        String texto = q.toLowerCase(Locale.ROOT);
        return (user.getEmail() != null && user.getEmail().toLowerCase(Locale.ROOT).contains(texto))
                || (user.getNombres() != null && user.getNombres().toLowerCase(Locale.ROOT).contains(texto))
                || (user.getApellidos() != null && user.getApellidos().toLowerCase(Locale.ROOT).contains(texto))
                || (user.getDni() != null && user.getDni().contains(q));
    }

    /** Ficha completa: sección/matrícula del año ABIERTO + apoderados vivos. */
    private EstudianteResponse aRespuesta(Estudiante estudiante) {
        User user = estudiante.getUser();
        Optional<Matricula> vigente = matriculaService.vigenteDe(estudiante.getId());
        List<EstudianteResponse.Vinculo> apoderados =
                vinculoRepository.findByEstudianteIdAndActivoTrue(estudiante.getId()).stream()
                        .map(vinculo -> new EstudianteResponse.Vinculo(
                                vinculo.getApoderado().getId(),
                                vinculo.getApoderado().getUser().getNombres() + " "
                                        + vinculo.getApoderado().getUser().getApellidos(),
                                vinculo.getParentesco(),
                                vinculo.isResponsablePrincipal()))
                        .toList();

        return new EstudianteResponse(
                estudiante.getId(),
                user.getEmail(),
                user.getNombres(),
                user.getApellidos(),
                user.getDni(),
                estudiante.getFechaNacimiento(),
                estudiante.getEstado(),
                user.isActivo(),
                vigente.map(matricula -> matricula.getSeccion().getEtiqueta()).orElse(null),
                vigente.map(matricula -> matricula.getSeccion().getAnio().getNombre()).orElse(null),
                vigente.map(Matricula::getFechaMatricula).orElse(null),
                vigente.map(Matricula::getSituacionFinal).orElse(null),
                apoderados
        );
    }
}
