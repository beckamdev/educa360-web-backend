package com.educa360.backend.service;

import com.educa360.backend.dto.UsuarioBasicoRequest;
import com.educa360.backend.dto.UsuarioResponse;
import com.educa360.backend.dto.UsuarioUpdateRequest;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Telefono;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Ciclo de vida de la CUENTA de acceso: creación de la credencial, listado,
 * edición de los datos básicos, baja lógica y reseteo de contraseña.
 *
 * El perfil de cada rol NO se crea aquí: lo hacen los servicios de cada rol
 * (DocenteService, EstudianteService, SecretariaService, ApoderadoService)
 * llamando a {@link #crearCuenta(UsuarioBasicoRequest, User.Role)}. Así cada
 * regla vive en la clase que la conoce (p. ej. la de "un estudiante no tiene
 * teléfonos" está en EstudianteService), y sólo ADMIN, que no tiene perfil,
 * se da de alta directamente desde este servicio.
 *
 * Consistencia perfil ↔ rol: como el rol lo fija el servicio que llama, es
 * imposible crear una cuenta con un rol que no corresponda a su perfil.
 */
@Service
public class UsuarioService {

    private final UserRepository userRepository;
    private final DocenteRepository docenteRepository;
    private final EstudianteRepository estudianteRepository;
    private final SecretariaRepository secretariaRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UserRepository userRepository,
            DocenteRepository docenteRepository,
            EstudianteRepository estudianteRepository,
            SecretariaRepository secretariaRepository,
            ApoderadoRepository apoderadoRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.docenteRepository = docenteRepository;
        this.estudianteRepository = estudianteRepository;
        this.secretariaRepository = secretariaRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== Creación de la cuenta ====================

    /**
     * Crea la credencial de acceso (User) con las validaciones comunes:
     * email y DNI no repetidos, contraseña hasheada y teléfonos.
     *
     * Método compartido: lo usan los servicios de cada perfil y el alta de
     * administradores. No toca ningún perfil (el caller decide el rol y crea
     * su perfil en la misma transacción).
     */
    @Transactional
    public User crearCuenta(UsuarioBasicoRequest datos, User.Role rol) {
        if (userRepository.existsByEmail(datos.email())) {
            throw new ConflictException("El email " + datos.email() + " ya está registrado");
        }
        if (userRepository.existsByDni(datos.dni())) {
            throw new ConflictException("El DNI " + datos.dni() + " ya está registrado");
        }

        User user = new User(
                datos.email(),
                passwordEncoder.encode(datos.password()),
                datos.nombres(),
                datos.apellidos(),
                datos.dni(),
                rol
        );
        if (datos.telefonos() != null) {
            datos.telefonos().forEach(numero -> user.agregarTelefono(new Telefono(numero)));
        }
        return userRepository.save(user);
    }

    /**
     * Alta de administrador: ADMIN no tiene perfil propio, así que su cuenta
     * es todo lo que existe. Éste es el único rol que se crea aquí.
     */
    @Transactional
    public UsuarioResponse crearAdmin(UsuarioBasicoRequest datos) {
        return aRespuesta(crearCuenta(datos, User.Role.ADMIN));
    }

    // ==================== Consultas ====================

    /**
     * Listado con filtros combinables (todos opcionales).
     * La búsqueda 'q' cubre email, nombres, apellidos y DNI.
     */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(User.Role rol, Boolean activo, String q) {
        List<Specification<User>> filtros = new ArrayList<>();

        if (rol != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("rol"), rol));
        }
        if (activo != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("activo"), activo));
        }
        if (q != null && !q.isBlank()) {
            String patron = "%" + q.trim().toLowerCase() + "%";
            filtros.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), patron),
                    cb.like(cb.lower(root.get("nombres")), patron),
                    cb.like(cb.lower(root.get("apellidos")), patron),
                    cb.like(root.get("dni"), patron)));
        }

        Specification<User> spec = filtros.isEmpty()
                ? null
                : filtros.stream().reduce(Specification::and).orElse(null);

        List<User> usuarios = (spec == null)
                ? userRepository.findAll(Sort.by("id"))
                : userRepository.findAll(spec, Sort.by("id"));

        return usuarios.stream().map(this::aRespuesta).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return aRespuesta(buscar(id));
    }

    // ==================== Modificaciones ====================

    /**
     * Edita los datos básicos, los teléfonos y los campos del perfil.
     * Teléfonos: null = no tocarlos, lista vacía = quitar todos.
     */
    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest request) {
        User user = buscar(id);

        user.setNombres(request.nombres());
        user.setApellidos(request.apellidos());

        if (request.telefonos() != null) {
            user.getTelefonos().clear(); // orphanRemoval borra los que ya no están
            // Mismo motivo que en reemplazarTelefonos: el DELETE debe ir antes
            // del INSERT para no chocar con unique(user_id, numero) cuando se
            // guarda la misma lista que ya tenía.
            userRepository.flush();
            request.telefonos().forEach(numero -> user.agregarTelefono(new Telefono(numero)));
        }

        if (user.getRol() == User.Role.DOCENTE && request.titulo() != null) {
            docenteRepository.findByUser(user).ifPresent(docente -> docente.setTitulo(request.titulo()));
        }
        if (user.getRol() == User.Role.ESTUDIANTE && request.fechaNacimiento() != null) {
            estudianteRepository.findByUser(user)
                    .ifPresent(estudiante -> estudiante.setFechaNacimiento(request.fechaNacimiento()));
        }

        User guardado = userRepository.save(user);
        return aRespuesta(guardado);
    }

    /**
     * El usuario autenticado reemplaza SUS propios teléfonos.
     *
     * Misma semántica que la edición de ADMIN: la lista enviada es la que
     * queda, y una lista vacía los quita todos. La identidad sale del token,
     * así que nadie puede editar los teléfonos de otra cuenta.
     *
     * El ESTUDIANTE no gestiona teléfonos: al ser menor, la comunicación es
     * con su apoderado (misma regla que aplica EstudianteService al darlo de
     * alta). Se responde 403.
     */
    @Transactional
    public void reemplazarTelefonos(String email, List<String> telefonos) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (user.getRol() == User.Role.ESTUDIANTE) {
            throw new AccessDeniedException(
                    "El estudiante no gestiona teléfonos: la comunicación es con su apoderado");
        }
        if (telefonos != null && new HashSet<>(telefonos).size() != telefonos.size()) {
            throw new BadRequestException("No se puede repetir el mismo teléfono");
        }

        user.getTelefonos().clear(); // orphanRemoval borra los que ya no están
        // Flush explícito: si la nueva lista repite un número que ya existía,
        // el INSERT chocaría con unique(user_id, numero) si Hibernate lo
        // ejecutara antes del DELETE. Aquí el borrado va primero.
        userRepository.flush();
        if (telefonos != null) {
            telefonos.forEach(numero -> user.agregarTelefono(new Telefono(numero)));
        }
        userRepository.save(user);
    }

    /** Baja lógica: la cuenta deja de poder entrar; sus datos permanecen. */    @Transactional
    public UsuarioResponse cambiarActivo(Long id, boolean activo) {
        User user = buscar(id);
        user.setActivo(activo);
        return aRespuesta(userRepository.save(user));
    }

    /**
     * Reseteo de contraseña por ADMIN.
     * OJO: esto NO invalida los tokens ya emitidos (API stateless); siguen
     * siendo válidos hasta expirar. Cortar el acceso al instante es la baja.
     */
    @Transactional
    public UsuarioResponse restablecerPassword(Long id, String passwordNueva) {
        User user = buscar(id);
        user.setPassword(passwordEncoder.encode(passwordNueva));
        return aRespuesta(userRepository.save(user));
    }

    // ==================== Helpers ====================

    /** Monta la respuesta de administración leyendo el perfil de su rol. */
    public UsuarioResponse aRespuesta(User user) {
        return switch (user.getRol()) {
            case DOCENTE -> docenteRepository.findByUser(user)
                    .map(docente -> UsuarioResponse.desde(user, docente.getTitulo(), null, null))
                    .orElseGet(() -> UsuarioResponse.desde(user, null, null, null));
            case ESTUDIANTE -> estudianteRepository.findByUser(user)
                    .map(estudiante -> UsuarioResponse.desde(user, null,
                            estudiante.getFechaNacimiento(), estudiante.getEstado().name()))
                    .orElseGet(() -> UsuarioResponse.desde(user, null, null, null));
            case SECRETARIA, APODERADO, ADMIN ->
                    UsuarioResponse.desde(user, null, null, null);
        };
    }

    private User buscar(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("El usuario " + id + " no existe"));
    }
}
