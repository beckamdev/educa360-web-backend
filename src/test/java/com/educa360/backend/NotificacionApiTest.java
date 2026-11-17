package com.educa360.backend;

import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.ApoderadoEstudiante;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.User;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.DispositivoRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.NotificacionRepository;
import com.educa360.backend.repository.SeccionRepository;
import com.educa360.backend.repository.UserRepository;
import com.educa360.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Tests de la API de notificaciones.
 *
 * Cubre:
 * - un comunicado genera una notificación persistente por destinatario;
 * - el usuario solo ve y marca las suyas;
 * - el contador refleja las no leídas;
 * - sin token responde 401.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificacionApiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String NOTIFICACIONES = "/api/v1/notificaciones";
    private static final String DISPOSITIVOS = "/api/v1/notificaciones/dispositivos";
    private static final String COMUNICADOS = "/api/v1/admin/comunicados";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AnioEscolarRepository anioRepository;
    @Autowired private SeccionRepository seccionRepository;
    @Autowired private EstudianteRepository estudianteRepository;
    @Autowired private ApoderadoRepository apoderadoRepository;
    @Autowired private ApoderadoEstudianteRepository vinculoRepository;
    @Autowired private MatriculaRepository matriculaRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private DispositivoRepository dispositivoRepository;

    private User admin;
    private User alumnoUser;
    private User mamaUser;
    private AnioEscolar anio;
    private String tokenAdmin;
    private String tokenAlumno;
    private String tokenMama;

    @BeforeEach
    void empezarDeCero() {
        notificacionRepository.deleteAll();
        dispositivoRepository.deleteAll();
        matriculaRepository.deleteAll();
        vinculoRepository.deleteAll();
        estudianteRepository.deleteAll();
        apoderadoRepository.deleteAll();
        seccionRepository.deleteAll();
        anioRepository.deleteAll();
        userRepository.deleteAll();

        admin = user("admin@educa360.pe", "10000001", User.Role.ADMIN);
        alumnoUser = user("alma@educa360.pe", "30000001", User.Role.ESTUDIANTE);
        mamaUser = user("lucia@educa360.pe", "40000001", User.Role.APODERADO);
        userRepository.saveAll(List.of(admin, alumnoUser, mamaUser));

        anio = new AnioEscolar(2026, "Año Escolar 2026", LocalDate.of(2026, 3, 2));
        anio.setEstado(AnioEscolar.Estado.ABIERTO);
        anioRepository.save(anio);

        Seccion seccion = new Seccion(3, "A", "A-101", null, anio);
        seccionRepository.save(seccion);

        Estudiante alumno = new Estudiante(alumnoUser, LocalDate.of(2011, 3, 3));
        estudianteRepository.save(alumno);

        matriculaRepository.save(new Matricula(alumno, anio, seccion,
                LocalDate.of(2026, 2, 20)));

        Apoderado mama = new Apoderado(mamaUser);
        apoderadoRepository.save(mama);
        vinculoRepository.save(new ApoderadoEstudiante(
                mama, alumno, ApoderadoEstudiante.Parentesco.MADRE, true));

        tokenAdmin = jwtService.generateToken(admin);
        tokenAlumno = jwtService.generateToken(alumnoUser);
        tokenMama = jwtService.generateToken(mamaUser);
    }

    @Test
    @DisplayName("Un comunicado genera notificación y se puede marcar como leída")
    void comunicadoGeneraNotificacion() throws Exception {
        MvcResult creado = mockMvc.perform(post(COMUNICADOS)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":null,"contenido":"Bienvenidos al año"}"""
                                .formatted(anio.getId())))
                .andReturn();
        assertEquals(201, creado.getResponse().getStatus(),
                creado.getResponse().getContentAsString());

        String listado = conToken(get(NOTIFICACIONES), tokenAlumno)
                .getResponse().getContentAsString();
        assertTrue(listado.contains("\"tipo\":\"COMUNICADO\""), listado);
        assertTrue(listado.contains("\"leida\":false"), listado);
        assertTrue(listado.contains("Bienvenidos al año"), listado);

        long idNotificacion = idDe(listado);

        String contador = conToken(get(NOTIFICACIONES + "/contador"), tokenAlumno)
                .getResponse().getContentAsString();
        assertTrue(contador.contains("\"noLeidas\":1"), contador);

        MvcResult marcada = conToken(
                patch(NOTIFICACIONES + "/" + idNotificacion + "/leida"), tokenAlumno);
        assertEquals(200, marcada.getResponse().getStatus());
        assertTrue(marcada.getResponse().getContentAsString().contains("\"leida\":true"));

        String contadorCero = conToken(get(NOTIFICACIONES + "/contador"), tokenAlumno)
                .getResponse().getContentAsString();
        assertTrue(contadorCero.contains("\"noLeidas\":0"), contadorCero);

        String noLeidas = conToken(get(NOTIFICACIONES + "?leida=false"), tokenAlumno)
                .getResponse().getContentAsString();
        assertFalse(noLeidas.contains("\"id\":" + idNotificacion), noLeidas);

        String leidas = conToken(get(NOTIFICACIONES + "?leida=true"), tokenAlumno)
                .getResponse().getContentAsString();
        assertTrue(leidas.contains("\"id\":" + idNotificacion), leidas);
    }

    @Test
    @DisplayName("Un usuario no puede marcar la notificación de otro")
    void noPuedeMarcarAjena() throws Exception {
        mockMvc.perform(post(COMUNICADOS)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":null,"contenido":"Aviso"}"""
                                .formatted(anio.getId())))
                .andReturn();

        String listadoAlumno = conToken(get(NOTIFICACIONES), tokenAlumno)
                .getResponse().getContentAsString();
        long idAlumno = idDe(listadoAlumno);

        assertEquals(404, conToken(
                patch(NOTIFICACIONES + "/" + idAlumno + "/leida"), tokenMama)
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("Marcar todas las leídas deja el contador en cero")
    void marcarTodas() throws Exception {
        mockMvc.perform(post(COMUNICADOS)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":null,"contenido":"Aviso"}"""
                                .formatted(anio.getId())))
                .andReturn();

        MvcResult resultado = conToken(
                patch(NOTIFICACIONES + "/leidas"), tokenAlumno);
        assertEquals(200, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString().contains("\"noLeidas\":0"));

        String contador = conToken(get(NOTIFICACIONES + "/contador"), tokenAlumno)
                .getResponse().getContentAsString();
        assertTrue(contador.contains("\"noLeidas\":0"), contador);
    }

    @Test
    @DisplayName("Sin token la bandeja responde 401")
    void sinToken() throws Exception {
        assertEquals(401, mockMvc.perform(get(NOTIFICACIONES))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get(NOTIFICACIONES + "/contador"))
                .andReturn().getResponse().getStatus());
    }

    // ==================== Dispositivos de push ====================

    @Test
    @DisplayName("El registro de dispositivo es idempotente y se puede listar")
    void registraDispositivoIdempotente() throws Exception {
        String cuerpo = """
                {"token":"ExponentPushToken[abc]","plataforma":"ANDROID"}""";

        MvcResult primero = conToken(post(DISPOSITIVOS)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo), tokenAlumno);
        assertEquals(200, primero.getResponse().getStatus(),
                primero.getResponse().getContentAsString());
        assertTrue(primero.getResponse().getContentAsString()
                .contains("ExponentPushToken[abc]"));

        assertEquals(200, conToken(post(DISPOSITIVOS)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo), tokenAlumno)
                .getResponse().getStatus());
        assertEquals(1, dispositivoRepository.count(),
                "el mismo token no duplica filas");

        String listado = conToken(get(DISPOSITIVOS), tokenAlumno)
                .getResponse().getContentAsString();
        assertTrue(listado.contains("\"plataforma\":\"ANDROID\""), listado);
    }

    @Test
    @DisplayName("El mismo token se reasigna al usuario que inicia sesión")
    void tokenSeReasignaAlNuevoUsuario() throws Exception {
        String cuerpo = """
                {"token":"ExponentPushToken[compartido]","plataforma":"IOS"}""";

        assertEquals(200, conToken(post(DISPOSITIVOS)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo), tokenAlumno)
                .getResponse().getStatus());
        assertEquals(200, conToken(post(DISPOSITIVOS)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo), tokenMama)
                .getResponse().getStatus());

        String deAlumno = conToken(get(DISPOSITIVOS), tokenAlumno)
                .getResponse().getContentAsString();
        String deMama = conToken(get(DISPOSITIVOS), tokenMama)
                .getResponse().getContentAsString();
        assertFalse(deAlumno.contains("compartido"),
                "el token ya no pertenece al alumno: " + deAlumno);
        assertTrue(deMama.contains("compartido"), deMama);
    }

    @Test
    @DisplayName("El DELETE es idempotente y no borra el token de otra cuenta")
    void eliminaSoloElPropio() throws Exception {
        String token = "ExponentPushToken[propio]";
        String cuerpo = """
                {"token":"%s","plataforma":"ANDROID"}""".formatted(token);
        conToken(post(DISPOSITIVOS)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo), tokenAlumno);

        assertEquals(204, conToken(delete(DISPOSITIVOS).param("token", token), tokenMama)
                .getResponse().getStatus());
        assertTrue(conToken(get(DISPOSITIVOS), tokenAlumno)
                .getResponse().getContentAsString().contains("propio"),
                "la otra cuenta no puede borrarlo");

        assertEquals(204, conToken(delete(DISPOSITIVOS).param("token", token), tokenAlumno)
                .getResponse().getStatus());
        assertFalse(conToken(get(DISPOSITIVOS), tokenAlumno)
                .getResponse().getContentAsString().contains("propio"));

        assertEquals(204, conToken(delete(DISPOSITIVOS).param("token", token), tokenAlumno)
                .getResponse().getStatus(), "borrar dos veces no falla");
    }

    @Test
    @DisplayName("El registro valida token y plataforma")
    void validaRegistroDeDispositivo() throws Exception {
        assertEquals(400, conToken(post(DISPOSITIVOS)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"token":"  ","plataforma":"ANDROID"}"""), tokenAlumno)
                .getResponse().getStatus());
        assertEquals(400, conToken(post(DISPOSITIVOS)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"token":"ExponentPushToken[abc]"}"""), tokenAlumno)
                .getResponse().getStatus());
        assertEquals(400, conToken(post(DISPOSITIVOS)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"token":"ExponentPushToken[abc]","plataforma":"WINDOWS"}"""), tokenAlumno)
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("Sin token los dispositivos responden 401")
    void dispositivosSinToken() throws Exception {
        assertEquals(401, mockMvc.perform(get(DISPOSITIVOS))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(post(DISPOSITIVOS)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"token":"ExponentPushToken[abc]","plataforma":"ANDROID"}"""))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(delete(DISPOSITIVOS).param("token", "x"))
                .andReturn().getResponse().getStatus());
    }

    // ==================== Helpers ====================

    private User user(String email, String dni, User.Role rol) {
        return new User(email, passwordEncoder.encode("Password1234"),
                "Nombre", "Apellido", dni, rol);
    }

    private MvcResult conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private long idDe(String json) {
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertTrue(matcher.find(), "la respuesta debe traer el id: " + json);
        return Long.parseLong(matcher.group(1));
    }
}
