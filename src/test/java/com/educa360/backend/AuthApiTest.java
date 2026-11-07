package com.educa360.backend;

import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.UserRepository;
import com.educa360.backend.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Tests de integración de la API de autenticación + RBAC.
 *
 * Usa una PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo
 * y se destruye al terminar. Si no hay Docker, los tests se saltan en lugar
 * de fallar.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\":\"([^\"]*)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocenteRepository docenteRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private SecretariaRepository secretariaRepository;

    @Autowired
    private ApoderadoRepository apoderadoRepository;

    @Autowired
    private LoginAttemptService loginAttempts;

    @BeforeEach
    void empezarDeCero() {
        // Los perfiles tienen FK hacia users: se borran primero
        docenteRepository.deleteAll();
        estudianteRepository.deleteAll();
        secretariaRepository.deleteAll();
        apoderadoRepository.deleteAll();
        userRepository.deleteAll(); // cada test ve una BD vacía (alta inicial posible)
        loginAttempts.limpiar();    // y sin intentos fallidos acumulados
    }

    // ==================== Autenticación ====================

    @Test
    @DisplayName("GET /me sin token responde 401")
    void sinTokenResponde401() throws Exception {
        assertEquals(401, mockMvc.perform(get("/api/v1/auth/me")).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("El registro crea SIEMPRE administradores (el resto de roles tiene su endpoint)")
    void registroCreaAdministradores() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("primer@educa360.pe", "Secreta123", "11112222")))
                .andReturn();

        assertEquals(201, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString().contains("\"rol\":\"ADMIN\""),
                "El registro sólo crea ADMIN: los demás roles pasan por /api/v1/admin/*");
    }

    @Test
    @DisplayName("El registro se cierra tras la primera cuenta, incluso con token de ADMIN")
    void registroSeCierraTrasLaPrimeraCuenta() throws Exception {
        String admin = registrar("admin@educa360.pe", "Admin12345", "12345678", null);

        assertEquals(403, enviarPost("/api/v1/auth/register",
                cuerpo("intruso@x.pe", "Intruso123", "99998888"), null).getResponse().getStatus());
        assertEquals(403, enviarPost("/api/v1/auth/register",
                cuerpo("otro@x.pe", "OtraClave123", "87654321"), admin).getResponse().getStatus(),
                "ni siquiera un ADMIN debe usar /auth/register: eso lo hace /admin/usuarios");
        assertNotNull(admin);
    }

    @Test
    @DisplayName("Login correcto devuelve token y el perfil, SIN la contraseña")
    void loginCorrectoDevuelveTokenSinPassword() throws Exception {
        registrar("admin@educa360.pe", "Admin12345", "12345678", null);

        MvcResult login = enviarPost("/api/v1/auth/login",
                loginBody("admin@educa360.pe", "Admin12345"), null);
        String cuerpo = login.getResponse().getContentAsString();

        assertEquals(200, login.getResponse().getStatus());
        assertNotNull(tokenDe(cuerpo), "Debe venir el JWT");
        assertTrue(cuerpo.contains("\"tokenType\":\"Bearer\""));
        assertTrue(!cuerpo.contains("password"), "La contraseña jamás debe salir en la respuesta");

        // y con ese token, /me devuelve el perfil
        MvcResult me = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tokenDe(cuerpo)))
                .andReturn();
        assertEquals(200, me.getResponse().getStatus());
        assertTrue(me.getResponse().getContentAsString().contains("admin@educa360.pe"));
        assertTrue(!me.getResponse().getContentAsString().contains("password"));
    }

    @Test
    @DisplayName("Contraseña incorrecta o email inexistente: 401 con el mismo mensaje")
    void credencialesInvalidasResponde401() throws Exception {
        registrar("admin@educa360.pe", "Admin12345", "12345678", null);

        MvcResult mala = enviarPost("/api/v1/auth/login",
                loginBody("admin@educa360.pe", "clave-mala"), null);
        MvcResult inexistente = enviarPost("/api/v1/auth/login",
                loginBody("nadie@x.pe", "Loquesea123"), null);

        assertEquals(401, mala.getResponse().getStatus());
        assertEquals(401, inexistente.getResponse().getStatus());
        // Sin enumeración de usuarios: mismo mensaje en ambos casos
        assertNotNull(mensajeDe(mala));
        assertEquals(mensajeDe(mala), mensajeDe(inexistente));
    }

    @Test
    @DisplayName("El email no distingue mayúsculas ni espacios")
    void emailSeNormaliza() throws Exception {
        registrar("  Lucila@Educa360.pe ", "Lucila1234", "22223333", null);

        MvcResult login = enviarPost("/api/v1/auth/login",
                loginBody("LUCILA@educa360.pe", "Lucila1234"), null);

        assertEquals(200, login.getResponse().getStatus());
        assertEquals(1, userRepository.count());
    }

    @Test
    @DisplayName("DNI sin 8 dígitos se rechaza con 400")
    void dniInvalidoResponde400() throws Exception {
        MvcResult resultado = enviarPost("/api/v1/auth/register",
                cuerpo("dni@x.pe", "Password123", "12A45"), null);

        assertEquals(400, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString().contains("8 dígitos"));
    }

    @Test
    @DisplayName("Rol con un valor que no existe responde 400, no 500")
    void rolInvalidoResponde400() throws Exception {
        String token = registrar("admin@educa360.pe", "Admin12345", "12345678", null);

        // El rol ya no es un campo del registro; ahora viaja como filtro del listado
        MvcResult resultado = mockMvc.perform(get("/api/v1/admin/usuarios?rol=FOO")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        assertEquals(400, resultado.getResponse().getStatus());
    }

    @Test
    @DisplayName("JSON malformado responde 400, no 500")
    void jsonMalformadoResponde400() throws Exception {
        MvcResult resultado = enviarPost("/api/v1/auth/login", "{esto no es json", null);

        assertEquals(400, resultado.getResponse().getStatus());
    }

    @Test
    @DisplayName("Ruta inexistente responde 404 y método no permitido 405")
    void erroresDeRouting() throws Exception {
        String token = registrar("admin@educa360.pe", "Admin12345", "12345678", null);

        MvcResult noExiste = mockMvc.perform(get("/api/v1/noexiste")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        MvcResult metodoNoPermitido = mockMvc.perform(put("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertEquals(404, noExiste.getResponse().getStatus());
        assertEquals(405, metodoNoPermitido.getResponse().getStatus());
    }

    @Test
    @DisplayName("Cuenta dada de baja no puede iniciar sesión (403)")
    void cuentaDesactivadaNoPuedeLoguear() throws Exception {
        registrar("baja@educa360.pe", "Baja12345", "33334444", null);
        userRepository.findByEmail("baja@educa360.pe").ifPresent(usuario -> {
            usuario.setActivo(false);
            userRepository.save(usuario);
        });

        MvcResult login = enviarPost("/api/v1/auth/login",
                loginBody("baja@educa360.pe", "Baja12345"), null);

        assertEquals(403, login.getResponse().getStatus());
        assertTrue(login.getResponse().getContentAsString().contains("inactivo"));
    }

    @Test
    @DisplayName("Tras 5 fallos el login se bloquea: 429 con Retry-After")
    void fuerzaBrutaBloqueaCon429() throws Exception {
        registrar("segura@educa360.pe", "Secreta1234", "44445555", null);

        for (int i = 1; i <= 5; i++) {
            MvcResult fallo = enviarPost("/api/v1/auth/login",
                    loginBody("segura@educa360.pe", "mala-" + i), null);
            assertEquals(401, fallo.getResponse().getStatus(), "intento " + i);
        }

        // El 6.º intento, incluso con la contraseña CORRECTA, sigue bloqueado
        MvcResult bloqueado = enviarPost("/api/v1/auth/login",
                loginBody("segura@educa360.pe", "Secreta1234"), null);

        assertEquals(429, bloqueado.getResponse().getStatus());
        assertNotNull(bloqueado.getResponse().getHeader("Retry-After"),
                "debe indicar cuándo volver a intentar");
        assertTrue(bloqueado.getResponse().getContentAsString().contains("429"));
    }

    @Test
    @DisplayName("Un login correcto reinicia el contador de intentos")
    void aciertoReiniciaElContador() throws Exception {
        registrar("buena@educa360.pe", "Buena12345", "66667777", null);

        for (int i = 1; i <= 4; i++) {
            enviarPost("/api/v1/auth/login", loginBody("buena@educa360.pe", "mala-" + i), null);
        }
        // Acierto: limpia los 4 fallos anteriores
        assertEquals(200, enviarPost("/api/v1/auth/login",
                loginBody("buena@educa360.pe", "Buena12345"), null).getResponse().getStatus());

        // Otros 4 fallos: con el contador limpio no debe bloquear
        for (int i = 1; i <= 4; i++) {
            assertEquals(401, enviarPost("/api/v1/auth/login",
                    loginBody("buena@educa360.pe", "mala-" + i), null).getResponse().getStatus(),
                    "intento " + i + " tras el acierto");
        }
    }

    // ==================== RBAC ====================

    @Test
    @DisplayName("Sólo el rol correcto entra en cada zona")
    void rbacPorRol() throws Exception {
        String tokenAdmin = registrar("admin@educa360.pe", "Admin12345", "12345678", null);

        // El estudiante se da de alta en su endpoint (allí se crea su perfil)
        assertEquals(201, enviarPost("/api/v1/secretaria/estudiantes", """
                        {"usuario":{"email":"lucia@educa360.pe","password":"Lucia12345",
                                    "nombres":"Lucia","apellidos":"Gomez","dni":"87654321"},
                         "perfil":{"fechaNacimiento":"2010-05-12"}}""", tokenAdmin)
                .getResponse().getStatus());
        String tokenEstudiante = tokenDe(enviarPost("/api/v1/auth/login",
                loginBody("lucia@educa360.pe", "Lucia12345"), null).getResponse().getContentAsString());

        // La secretaria también la crea el ADMIN (su perfil todavía no tiene campos)
        assertEquals(201, enviarPost("/api/v1/admin/secretarias", """
                        {"usuario":{"email":"sofia@educa360.pe","password":"Sofia12345",
                                    "nombres":"Sofia","apellidos":"Quispe","dni":"11223344"},
                         "perfil":{}}""", tokenAdmin)
                .getResponse().getStatus());
        String tokenSecretaria = tokenDe(enviarPost("/api/v1/auth/login",
                loginBody("sofia@educa360.pe", "Sofia12345"), null).getResponse().getContentAsString());

        // Zona ADMIN: sólo ADMIN
        assertEquals(200, getConToken("/api/v1/admin/usuarios", tokenAdmin));
        assertEquals(403, getConToken("/api/v1/admin/usuarios", tokenEstudiante));
        assertEquals(403, getConToken("/api/v1/admin/usuarios", tokenSecretaria));

        // Zona SECRETARIA: sólo SECRETARIA
        assertEquals(200, getConToken("/api/v1/secretaria/tickets", tokenSecretaria));
        assertEquals(403, getConToken("/api/v1/secretaria/tickets", tokenAdmin));
        assertEquals(403, getConToken("/api/v1/secretaria/tickets", tokenEstudiante));

        // Zona ESTUDIANTE: sólo ESTUDIANTE
        assertEquals(200, getConToken("/api/v1/estudiante/cursos", tokenEstudiante));
        assertEquals(403, getConToken("/api/v1/estudiante/cursos", tokenAdmin));
        assertEquals(403, getConToken("/api/v1/estudiante/cursos", tokenSecretaria));

        // Cualquier usuario autenticado; sin token, 401
        assertEquals(200, getConToken("/api/v1/auth/me", tokenEstudiante));
        assertEquals(401, getConToken("/api/v1/auth/me", null));
    }

    // ==================== Helpers ====================

    private MvcResult enviarPost(String url, String body, String bearer) throws Exception {
        var peticion = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (bearer != null) {
            peticion = peticion.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(peticion).andReturn();
    }

    private int getConToken(String url, String bearer) throws Exception {
        var peticion = get(url);
        if (bearer != null) {
            peticion = peticion.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(peticion).andReturn().getResponse().getStatus();
    }

    /**
     * Registra una cuenta (siempre ADMIN) y devuelve su token.
     * Ojo: registrar() falla si ya hay un ADMIN autenticado en el contexto
     * que no sea éste; por eso los tests sólo lo usan como primer usuario.
     */
    private String registrar(String email, String password, String dni, String bearer) throws Exception {
        MvcResult resultado = enviarPost("/api/v1/auth/register",
                cuerpo(email, password, dni), bearer);
        assertEquals(201, resultado.getResponse().getStatus(),
                "Registro inesperado: " + resultado.getResponse().getContentAsString());
        return tokenDe(resultado.getResponse().getContentAsString());
    }

    private String cuerpo(String email, String password, String dni) {
        return ("{\"email\":\"%s\",\"password\":\"%s\",\"nombres\":\"Prueba\"," +
                "\"apellidos\":\"Testeo\",\"dni\":\"%s\"}")
                .formatted(email, password, dni);
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String tokenDe(String cuerpo) {
        Matcher matcher = TOKEN_PATTERN.matcher(cuerpo);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String mensajeDe(MvcResult resultado) throws Exception {
        Matcher matcher = MESSAGE_PATTERN.matcher(resultado.getResponse().getContentAsString());
        return matcher.find() ? matcher.group(1) : null;
    }
}
