package com.educa360.backend.service;

import com.educa360.backend.dto.ArchivoDescargaResponse;
import com.educa360.backend.dto.MaterialResponse;
import com.educa360.backend.dto.MaterialUpdateRequest;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Contenido;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.MaterialClase;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.ContenidoRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MaterialClaseRepository;
import com.educa360.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Materiales de clase (Módulo 8).
 *
 * Reglas que gobierna:
 * - sólo el docente dueño del curso sube, edita, publica o da de baja;
 * - el material nace NO publicado y se publica con una acción explícita;
 * - un material no publicado sólo lo ven el docente y ADMIN;
 * - si cuelga de una unidad BORRADOR, el alumno no lo ve aunque esté publicado;
 * - el archivo vive en el bucket privado {@code materiales}; sólo se firma
 *   una URL temporal de 5 minutos para descargarlo;
 * - la baja es lógica y elimina el objeto del bucket.
 */
@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);
    private static final Duration TTL_DESCARGA = Duration.ofMinutes(5);

    private final MaterialClaseRepository materialRepository;
    private final CursoRepository cursoRepository;
    private final ContenidoRepository contenidoRepository;
    private final DocenteRepository docenteRepository;
    private final UserRepository userRepository;
    private final EstudianteRepository estudianteRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final ApoderadoEstudianteRepository vinculoRepository;
    private final MatriculaService matriculaService;
    private final StorageService storageService;
    private final ArchivoValidator archivoValidator;

    public MaterialService(MaterialClaseRepository materialRepository,
                           CursoRepository cursoRepository,
                           ContenidoRepository contenidoRepository,
                           DocenteRepository docenteRepository,
                           UserRepository userRepository,
                           EstudianteRepository estudianteRepository,
                           ApoderadoRepository apoderadoRepository,
                           ApoderadoEstudianteRepository vinculoRepository,
                           MatriculaService matriculaService,
                           StorageService storageService,
                           ArchivoValidator archivoValidator) {
        this.materialRepository = materialRepository;
        this.cursoRepository = cursoRepository;
        this.contenidoRepository = contenidoRepository;
        this.docenteRepository = docenteRepository;
        this.userRepository = userRepository;
        this.estudianteRepository = estudianteRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.vinculoRepository = vinculoRepository;
        this.matriculaService = matriculaService;
        this.storageService = storageService;
        this.archivoValidator = archivoValidator;
    }

    // ==================== Docente ====================

    @Transactional(readOnly = true)
    public List<MaterialResponse> listarDelCurso(String email, Long cursoId) {
        Docente docente = docenteDe(email);
        Curso curso = cursoDelDocente(docente, cursoId);
        return materialRepository
                .findByCursoIdAndActivoTrueOrderByCreatedAtDesc(curso.getId())
                .stream()
                .map(MaterialResponse::desde)
                .toList();
    }

    @Transactional
    public MaterialResponse crear(String email, Long cursoId, String titulo,
                                  String descripcion, Long contenidoId,
                                  MultipartFile archivo) {
        Docente docente = docenteDe(email);
        Curso curso = cursoDelDocente(docente, cursoId);
        Contenido contenido = contenidoDelCurso(curso, contenidoId);
        String tituloLimpio = tituloObligatorio(titulo);
        String descripcionLimpia = descripcionOpcional(descripcion);
        ArchivoValidator.ArchivoValidado validado =
                archivoValidator.validarMaterial(archivo);

        String path = rutaMaterial(curso.getId(), validado.nombreSeguro());
        storageService.upload(StorageService.BUCKET_MATERIALES, path,
                validado.contenido(), validado.mimeType());

        MaterialClase material = new MaterialClase(curso, docente, contenido,
                tituloLimpio, path, validado.nombreOriginal(),
                validado.mimeType(), validado.tamanoBytes());
        material.setDescripcion(descripcionLimpia);

        try {
            return MaterialResponse.desde(materialRepository.saveAndFlush(material));
        } catch (RuntimeException ex) {
            storageService.delete(StorageService.BUCKET_MATERIALES, path);
            throw ex;
        }
    }

    @Transactional
    public MaterialResponse actualizar(String email, Long materialId,
                                       MaterialUpdateRequest datos) {
        Docente docente = docenteDe(email);
        MaterialClase material = materialDelDocente(docente, materialId);
        Contenido contenido = contenidoDelCurso(material.getCurso(), datos.contenidoId());

        material.setTitulo(tituloObligatorio(datos.titulo()));
        material.setDescripcion(descripcionOpcional(datos.descripcion()));
        material.setContenido(contenido);
        return MaterialResponse.desde(materialRepository.save(material));
    }

    @Transactional
    public MaterialResponse reemplazarArchivo(String email, Long materialId,
                                              MultipartFile archivo) {
        Docente docente = docenteDe(email);
        MaterialClase material = materialDelDocente(docente, materialId);
        ArchivoValidator.ArchivoValidado validado =
                archivoValidator.validarMaterial(archivo);

        String pathNuevo = rutaMaterial(material.getCurso().getId(), validado.nombreSeguro());
        storageService.upload(StorageService.BUCKET_MATERIALES, pathNuevo,
                validado.contenido(), validado.mimeType());

        String pathAnterior = material.getStoragePath();
        material.setStoragePath(pathNuevo);
        material.setNombreOriginal(validado.nombreOriginal());
        material.setMimeType(validado.mimeType());
        material.setTamanoBytes(validado.tamanoBytes());

        try {
            MaterialResponse respuesta =
                    MaterialResponse.desde(materialRepository.saveAndFlush(material));
            storageService.delete(StorageService.BUCKET_MATERIALES, pathAnterior);
            return respuesta;
        } catch (RuntimeException ex) {
            storageService.delete(StorageService.BUCKET_MATERIALES, pathNuevo);
            throw ex;
        }
    }

    @Transactional
    public MaterialResponse cambiarPublicacion(String email, Long materialId,
                                               boolean publicado) {
        Docente docente = docenteDe(email);
        MaterialClase material = materialDelDocente(docente, materialId);

        if (publicado) {
            if (!material.isPublicado()) {
                material.publicar();
            }
        } else {
            material.despublicar();
        }
        return MaterialResponse.desde(materialRepository.save(material));
    }

    @Transactional
    public void eliminar(String email, Long materialId) {
        Docente docente = docenteDe(email);
        MaterialClase material = materialDelDocente(docente, materialId);

        material.setActivo(false);
        material.despublicar();
        materialRepository.save(material);

        try {
            storageService.delete(StorageService.BUCKET_MATERIALES,
                    material.getStoragePath());
        } catch (RuntimeException ex) {
            // El material ya quedó inactivo e inaccesible; el objeto huérfano
            // se puede limpiar después sin romper la baja lógica.
            log.warn("No se pudo eliminar el objeto del material {}", materialId, ex);
        }
    }

    // ==================== Estudiante, apoderado y ADMIN ====================

    @Transactional(readOnly = true)
    public List<MaterialResponse> listarDelEstudiante(String email, Long cursoId) {
        Estudiante estudiante = estudianteDe(email);
        Matricula matricula = matriculaVigente(estudiante);
        Curso curso = cursoDeLaSeccion(matricula, cursoId);
        return visiblesDe(curso);
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> listarDelApoderado(String email, Long hijoId, Long cursoId) {
        Apoderado apoderado = apoderadoDe(email);
        Estudiante estudiante = hijoVinculado(apoderado, hijoId);
        Matricula matricula = matriculaVigente(estudiante);
        Curso curso = cursoDeLaSeccion(matricula, cursoId);
        return visiblesDe(curso);
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> listarDelCursoAdmin(Long cursoId) {
        cursoRepository.findById(cursoId)
                .orElseThrow(() -> new NotFoundException("El curso " + cursoId + " no existe"));
        return materialRepository
                .findByCursoIdAndActivoTrueOrderByCreatedAtDesc(cursoId)
                .stream()
                .map(MaterialResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArchivoDescargaResponse descargar(String email, Long materialId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        MaterialClase material = materialRepository.findById(materialId)
                .orElseThrow(() -> new NotFoundException(
                        "El material " + materialId + " no existe"));
        if (!material.isActivo()) {
            throw new NotFoundException("El material " + materialId + " no existe");
        }

        switch (user.getRol()) {
            case ADMIN -> { /* lectura total, incluidos borradores */ }
            case DOCENTE -> validarDocentePuedeDescargar(user, material);
            case ESTUDIANTE -> validarEstudiantePuedeDescargar(user, material);
            case APODERADO -> validarApoderadoPuedeDescargar(user, material);
            default -> throw new AccessDeniedException(
                    "Este rol no tiene acceso a materiales");
        }

        String url = storageService.signedUrl(StorageService.BUCKET_MATERIALES,
                material.getStoragePath(), TTL_DESCARGA);
        return new ArchivoDescargaResponse(url, LocalDateTime.now().plus(TTL_DESCARGA));
    }

    // ==================== Helpers ====================

    private List<MaterialResponse> visiblesDe(Curso curso) {
        return materialRepository
                .findByCursoIdAndPublicadoTrueAndActivoTrueOrderByCreatedAtDesc(curso.getId())
                .stream()
                .filter(MaterialClase::esVisibleParaAlumno)
                .map(MaterialResponse::desde)
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
            throw new NotFoundException("El curso " + cursoId + " no existe");
        }
        return curso;
    }

    private MaterialClase materialDelDocente(Docente docente, Long materialId) {
        MaterialClase material = materialRepository.findById(materialId)
                .orElseThrow(() -> new NotFoundException(
                        "El material " + materialId + " no existe"));
        if (!material.isActivo()
                || !material.getCurso().getDocente().getId().equals(docente.getId())) {
            throw new NotFoundException("El material " + materialId + " no existe");
        }
        return material;
    }

    private Contenido contenidoDelCurso(Curso curso, Long contenidoId) {
        if (contenidoId == null) {
            return null;
        }
        Contenido contenido = contenidoRepository.findById(contenidoId)
                .orElseThrow(() -> new NotFoundException(
                        "El contenido " + contenidoId + " no existe"));
        if (!contenido.getUnidad().getCurso().getId().equals(curso.getId())) {
            throw new NotFoundException("El contenido " + contenidoId + " no existe");
        }
        return contenido;
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

    private Estudiante hijoVinculado(Apoderado apoderado, Long hijoId) {
        ApoderadoEstudiante vinculo = vinculoRepository
                .findByApoderadoIdAndEstudianteId(apoderado.getId(), hijoId)
                .filter(ApoderadoEstudiante::isActivo)
                .orElseThrow(() -> new NotFoundException(
                        "El estudiante " + hijoId + " no está vinculado a este apoderado"));
        Estudiante estudiante = vinculo.getEstudiante();
        if (!estudiante.estaHabilitado()) {
            throw new NotFoundException("El estudiante no está activo");
        }
        return estudiante;
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

    private void validarDocentePuedeDescargar(User user, MaterialClase material) {
        Docente docente = docenteRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException(
                        "El usuario no tiene perfil Docente"));
        if (!material.getCurso().getDocente().getId().equals(docente.getId())) {
            throw new NotFoundException("El material " + material.getId() + " no existe");
        }
    }

    private void validarEstudiantePuedeDescargar(User user, MaterialClase material) {
        Estudiante estudiante = estudianteDe(user.getEmail());
        Matricula matricula = matriculaVigente(estudiante);
        if (!material.getCurso().getSeccion().getId()
                .equals(matricula.getSeccion().getId())
                || !material.esVisibleParaAlumno()) {
            throw new NotFoundException("El material " + material.getId() + " no existe");
        }
    }

    private void validarApoderadoPuedeDescargar(User user, MaterialClase material) {
        Apoderado apoderado = apoderadoDe(user.getEmail());
        if (!material.esVisibleParaAlumno()) {
            throw new NotFoundException("El material " + material.getId() + " no existe");
        }
        boolean autorizado = vinculoRepository
                .findByApoderadoIdAndActivoTrue(apoderado.getId())
                .stream()
                .map(ApoderadoEstudiante::getEstudiante)
                .filter(Estudiante::estaHabilitado)
                .map(estudiante -> matriculaService.vigenteDe(estudiante.getId()))
                .flatMap(java.util.Optional::stream)
                .anyMatch(matricula -> matricula.getSeccion().getId()
                        .equals(material.getCurso().getSeccion().getId()));
        if (!autorizado) {
            throw new NotFoundException("El material " + material.getId() + " no existe");
        }
    }

    private String rutaMaterial(Long cursoId, String nombreSeguro) {
        return cursoId + "/" + UUID.randomUUID() + "-" + nombreSeguro;
    }

    private String tituloObligatorio(String titulo) {
        if (titulo == null || titulo.trim().isEmpty()) {
            throw new BadRequestException("El título es obligatorio");
        }
        String limpio = titulo.trim();
        if (limpio.length() > 255) {
            throw new BadRequestException("El título no puede superar los 255 caracteres");
        }
        return limpio;
    }

    private String descripcionOpcional(String descripcion) {
        if (descripcion == null || descripcion.trim().isEmpty()) {
            return null;
        }
        String limpio = descripcion.trim();
        if (limpio.length() > 2000) {
            throw new BadRequestException(
                    "La descripción no puede superar los 2000 caracteres");
        }
        return limpio;
    }
}
