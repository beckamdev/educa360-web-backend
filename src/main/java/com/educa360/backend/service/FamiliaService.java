package com.educa360.backend.service;

import com.educa360.backend.dto.FamiliaCreateRequest;
import com.educa360.backend.dto.FamiliaResponse;
import com.educa360.backend.dto.RepresentadoRequest;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.EstudianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Alta conjunta de una familia (Módulo 3): apoderado + estudiante + vínculo.
 *
 * Existe para resolver en UNA transacción el caso real de Secretaría:
 * - el apoderado ya existe (se busca por id o por DNI y se reutiliza);
 * - el apoderado no existe (se crea con los datos del formulario).
 *
 * Así el frontend no encadena tres peticiones ni deja registros huérfanos si
 * una de ellas falla. La matrícula sigue siendo un paso aparte.
 */
@Service
public class FamiliaService {

    private final UsuarioService usuarioService;
    private final ApoderadoService apoderadoService;
    private final ApoderadoRepository apoderadoRepository;
    private final EstudianteRepository estudianteRepository;

    public FamiliaService(UsuarioService usuarioService,
                          ApoderadoService apoderadoService,
                          ApoderadoRepository apoderadoRepository,
                          EstudianteRepository estudianteRepository) {
        this.usuarioService = usuarioService;
        this.apoderadoService = apoderadoService;
        this.apoderadoRepository = apoderadoRepository;
        this.estudianteRepository = estudianteRepository;
    }

    /**
     * Crea (o reutiliza) el apoderado, crea el estudiante y los vincula.
     *
     * Reglas:
     * - exactamente uno de {@code apoderadoId} o {@code nuevoApoderado};
     * - si el DNI de {@code nuevoApoderado} ya es de un APODERADO, se reutiliza;
     * - el estudiante no puede registrar teléfonos propios;
     * - un solo responsable principal por estudiante (lo garantiza el vínculo).
     */
    @Transactional
    public FamiliaResponse crear(FamiliaCreateRequest datos) {
        boolean porId = datos.apoderadoId() != null;
        boolean porDatos = datos.nuevoApoderado() != null;

        if (porId == porDatos) {
            throw new BadRequestException(
                    "Debes indicar apoderadoId o los datos del nuevo apoderado, pero no ambos");
        }

        Apoderado apoderado;
        boolean apoderadoCreado = false;

        if (porId) {
            apoderado = apoderadoRepository.findById(datos.apoderadoId())
                    .orElseThrow(() -> new NotFoundException(
                            "El apoderado " + datos.apoderadoId() + " no existe"));
        } else {
            Optional<Apoderado> existente =
                    apoderadoRepository.findByUserDni(datos.nuevoApoderado().dni());
            if (existente.isPresent()) {
                apoderado = existente.get();
            } else {
                User user = usuarioService.crearCuenta(
                        datos.nuevoApoderado(), User.Role.APODERADO);
                apoderado = apoderadoRepository.save(new Apoderado(user));
                apoderadoCreado = true;
            }
        }

        FamiliaCreateRequest.EstudianteNuevo nuevo = datos.estudiante();
        if (nuevo.usuario().telefonos() != null && !nuevo.usuario().telefonos().isEmpty()) {
            throw new BadRequestException(
                    "Un estudiante no registra teléfonos propios: la comunicación es con su apoderado");
        }

        User userEstudiante = usuarioService.crearCuenta(
                nuevo.usuario(), User.Role.ESTUDIANTE);
        Estudiante estudiante = estudianteRepository.save(
                new Estudiante(userEstudiante, nuevo.fechaNacimiento()));

        apoderadoService.vincular(apoderado.getId(), new RepresentadoRequest(
                estudiante.getId(), datos.parentesco(), datos.responsablePrincipal()));

        return new FamiliaResponse(
                apoderado.getId(),
                nombreCompleto(apoderado.getUser()),
                apoderado.getUser().getDni(),
                apoderadoCreado,
                estudiante.getId(),
                nombreCompleto(userEstudiante),
                userEstudiante.getDni(),
                datos.parentesco(),
                datos.responsablePrincipal()
        );
    }

    private String nombreCompleto(User user) {
        return user.getNombres() + " " + user.getApellidos();
    }
}
