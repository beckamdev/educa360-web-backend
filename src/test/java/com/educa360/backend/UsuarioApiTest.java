package com.educa360.backend;

import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Tests del módulo de gestión de usuarios y perfiles.
 *
 * Cada rol se da de alta en su endpoint con la forma
 * {"usuario": {...}, "perfil": {...}}; los servicios de cada rol son los que
 * crean la cuenta y su perfil en la misma transacción.
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioApiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

    private static final String USUARIOS = "/api/v1/admin/usuarios";

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

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        // Perfiles primero: tienen FK hacia users
        docenteRepository.deleteAll();
        estudianteRepository.deleteAll();
        secretariaRepository.deleteAll();
        apoderadoRepository.deleteAll();
        userRepository.deleteAll();

        // Alta inicial: el primer usuario de la BD siempre es ADMIN
        admin = registrarAdmin();
    }

    // ==================== Altas ====================

    @Test
    @DisplayName("Crear un DOCENTE en su endpoint crea su perfil con el título")
    void crearDocenteCreaSuPerfil() throws Exception {
        MvcResult resultado = crear("/api/v1/admin/docentes", docenteBody("doc1@educa360.pe", "20000001"));

        assertEquals(201, resultado.getResponse().getStatus());
        String cuerpo = resultado.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"rol\":\"DOCENTE\""));
        assertTrue(cuerpo.contains("Lic. en Matematicas"), "el título debe viajar en la respuesta");
        assertEquals(1, docenteRepository.count(), "debe existir el perfil Docente");
    }

    @Test
    @DisplayName("SECRETARIA se da de alta con perfil vacío (hoy no tiene campos)")
    void crearSecretariaConPerfilVacio() throws Exception {
        MvcResult resultado = crear("/api/v1/admin/secretarias", """
                {"usuario":{"email":"sec@educa360.pe","password":"Secreta1234","nombres":"Sara",
                            "apellidos":"Quintos","dni":"21000001","telefonos":["912345678"]},
                 "perfil":{}}""");

        assertEquals(201, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString().contains("\"rol\":\"SECRETARIA\""));
        assertEquals(1, secretariaRepository.count());
    }

    @Test
    @DisplayName("Un ESTUDIANTE exige fecha de nacimiento en su perfil")
    void estudianteSinFechaNacimientoResponde400() throws Exception {
        MvcResult resultado = crear("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"alu@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"30000001"},
                 "perfil":{}}""");

        assertEquals(400, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString().contains("fecha de nacimiento"));
    }

    @Test
    @DisplayName("Un ESTUDIANTE sin bloque 'perfil' responde 400 (no 500)")
    void estudianteSinPerfilResponde400() throws Exception {
        MvcResult resultado = crear("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"alu0@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"30000000"}}""");

        assertEquals(400, resultado.getResponse().getStatus());
    }

    @Test
    @DisplayName("Un ESTUDIANTE no puede tener teléfonos: la comunicación es con su apoderado")
    void estudianteConTelefonosResponde400() throws Exception {
        MvcResult resultado = crear("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"alu2@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"30000002","telefonos":["987654321"]},
                 "perfil":{"fechaNacimiento":"2010-05-12"}}""");

        assertEquals(400, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString().contains("apoderado"));
    }

    @Test
    @DisplayName("Email o DNI ya usados: 409 (en cualquier rol)")
    void duplicadosResponde409() throws Exception {
        assertEquals(201, crear("/api/v1/admin/docentes",
                docenteBody("dup@educa360.pe", "40000001")).getResponse().getStatus());

        assertEquals(409, crear("/api/v1/admin/docentes",
                docenteBody("dup@educa360.pe", "40000009")).getResponse().getStatus());
        assertEquals(409, crear("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"otro@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"40000001"},
                 "perfil":{"fechaNacimiento":"2010-05-12"}}""").getResponse().getStatus());
    }

    @Test
    @DisplayName("POST /admin/usuarios crea administradores, que no tienen perfil")
    void crearAdminNoCreaPerfil() throws Exception {
        MvcResult resultado = crear(USUARIOS, """
                {"email":"admin2@educa360.pe","password":"Admin23456","nombres":"Adrian",
                 "apellidos":"Muros","dni":"11000002"}""");

        assertEquals(201, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString().contains("\"rol\":\"ADMIN\""));
        assertEquals(0, docenteRepository.count() + estudianteRepository.count()
                + secretariaRepository.count() + apoderadoRepository.count());
    }

    // ==================== Autorización ====================

    @Test
    @DisplayName("SECRETARIA puede registrar estudiantes y apoderados; DOCENTE no")
    void secretariaRegistraAlumnosYApoderados() throws Exception {
        // Una secretaria creada por ADMIN...
        String tokenSecretaria = altaYToken("/api/v1/admin/secretarias", """
                {"usuario":{"email":"sec@educa360.pe","password":"Secreta1234","nombres":"Sara",
                            "apellidos":"Quintos","dni":"21000001","telefonos":["912345678"]},
                 "perfil":{}}""", "sec@educa360.pe", "Secreta1234");

        // ...puede dar de alta alumnos y familias en SU zona
        assertEquals(201, crearCon("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"alumna@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"22000001"},
                 "perfil":{"fechaNacimiento":"2010-05-12"}}""", tokenSecretaria)
                .getResponse().getStatus());
        assertEquals(201, crearCon("/api/v1/secretaria/apoderados", """
                {"usuario":{"email":"apod@educa360.pe","password":"Apoderado12","nombres":"Luis",
                            "apellidos":"Luna","dni":"22000002","telefonos":["911000111"]},
                 "perfil":{}}""", tokenSecretaria)
                .getResponse().getStatus());

        // ...mientras que un docente no da de alta a nadie
        String tokenDocente = altaYToken("/api/v1/admin/docentes",
                docenteBody("profe@educa360.pe", "22000003"), "profe@educa360.pe", "Docente1234");
        assertEquals(403, crearCon("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"otra@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"22000004"},
                 "perfil":{"fechaNacimiento":"2010-05-12"}}""", tokenDocente)
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("Sólo ADMIN puede gestionar usuarios y dar de alta el personal")
    void rbacDelModulo() throws Exception {
        String tokenEstudiante = crearYTokenearEstudiante();

        assertEquals(401, mockMvc.perform(get(USUARIOS)).andReturn().getResponse().getStatus());
        assertEquals(403, mockMvc.perform(get(USUARIOS).header("Authorization", "Bearer " + tokenEstudiante))
                .andReturn().getResponse().getStatus());
        assertEquals(403, mockMvc.perform(post("/api/v1/admin/docentes")
                        .contentType(MediaType.APPLICATION_JSON).content(docenteBody("nadie@educa360.pe", "99999999"))
                        .header("Authorization", "Bearer " + tokenEstudiante))
                .andReturn().getResponse().getStatus(),
                "un estudiante no puede dar de alta docentes");
        assertEquals(200, mockMvc.perform(get(USUARIOS).header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus());
    }

    // ==================== Consultas ====================

    @Test
    @DisplayName("El listado filtra por rol")
    void listarFiltraPorRol() throws Exception {
        assertEquals(201, crear("/api/v1/admin/docentes", docenteBody("docf@educa360.pe", "50000001"))
                .getResponse().getStatus());
        assertEquals(201, crear("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"aluf@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"50000002"},
                 "perfil":{"fechaNacimiento":"2010-05-12"}}""").getResponse().getStatus());

        MvcResult docentes = conToken(get(USUARIOS + "?rol=DOCENTE"), admin);
        MvcResult todos = conToken(get(USUARIOS), admin);

        String cuerpoDocentes = docentes.getResponse().getContentAsString();
        assertTrue(cuerpoDocentes.contains("docf@educa360.pe"));
        assertTrue(!cuerpoDocentes.contains("aluf@educa360.pe"), "el filtro debe excluir a los estudiantes");
        assertTrue(todos.getResponse().getContentAsString().contains("aluf@educa360.pe"));
    }

    @Test
    @DisplayName("Usuario inexistente: 404")
    void obtenerInexistenteResponde404() throws Exception {
        MvcResult resultado = conToken(get(USUARIOS + "/999"), admin);

        assertEquals(404, resultado.getResponse().getStatus());
    }

    // ==================== Modificaciones ====================

    @Test
    @DisplayName("La baja lógica corta el acceso al instante")
    void darDeBajaImpideElLogin() throws Exception {
        long id = idDe(crear("/api/v1/admin/secretarias", """
                {"usuario":{"email":"baja@educa360.pe","password":"Baja12345","nombres":"Berta",
                            "apellidos":"Gil","dni":"60000001","telefonos":["911222333"]},
                 "perfil":{}}"""));

        assertEquals(200, mockMvc.perform(patch(USUARIOS + "/" + id + "/activo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}")
                        .header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus());

        assertEquals(403, login("baja@educa360.pe", "Baja12345"), "dada de baja no debe poder entrar");
        assertTrue(userRepository.findByEmail("baja@educa360.pe").map(u -> !u.isActivo()).orElse(false));
    }

    @Test
    @DisplayName("El ADMIN puede resetear la contraseña y el usuario entra con la nueva")
    void restablecerPassword() throws Exception {
        long id = idDe(crear("/api/v1/admin/docentes", docenteBody("clave@educa360.pe", "70000001")));

        assertEquals(200, login("clave@educa360.pe", "Docente1234")); // la inicial sí sirve
        mockMvc.perform(patch(USUARIOS + "/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NuevaClave123\"}")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertEquals(401, login("clave@educa360.pe", "Docente1234"), "la vieja ya no sirve tras el reset");
        assertEquals(200, login("clave@educa360.pe", "NuevaClave123"));
    }

    @Test
    @DisplayName("Actualizar reemplaza los teléfonos y edita el perfil del rol")
    void actualizarReemplazaTelefonosYPerfil() throws Exception {
        long id = idDe(crear("/api/v1/admin/docentes", docenteBody("edit@educa360.pe", "80000001")));

        String cuerpo = actualizar(id, """
                {"nombres":"Dana Editada","apellidos":"Perez",
                 "telefonos":["987111222","987333444"],"titulo":"Maestría en Educación"}""");
        assertTrue(cuerpo.contains("987111222") && cuerpo.contains("987333444"));
        assertTrue(cuerpo.contains("Dana Editada"));
        assertTrue(cuerpo.contains("Maestría en Educación"), "el título del perfil se actualiza");

        String vacio = actualizar(id, """
                {"nombres":"Dana Editada","apellidos":"Perez","telefonos":[]}""");
        assertTrue(vacio.contains("\"telefonos\":[]"), "lista vacía debe quitar todos");
    }

    // ==================== Helpers ====================

    private MvcResult crear(String url, String body) throws Exception {
        return crearCon(url, body, admin);
    }

    private MvcResult crearCon(String url, String body, String token) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    /** Da de alta una cuenta (como ADMIN) y devuelve su token de login. */
    private String altaYToken(String url, String body, String email, String password) throws Exception {
        MvcResult creado = crearCon(url, body, admin);
        assertEquals(201, creado.getResponse().getStatus(),
                "no se pudo crear: " + creado.getResponse().getContentAsString());
        return loginYToken(email, password);
    }

    private MvcResult conToken(MockHttpServletRequestBuilder peticion, String token) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private String actualizar(Long id, String body) throws Exception {
        MvcResult resultado = mockMvc.perform(put(USUARIOS + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertEquals(200, resultado.getResponse().getStatus(),
                "edición inesperada: " + resultado.getResponse().getContentAsString());
        return resultado.getResponse().getContentAsString();
    }

    private String registrarAdmin() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@educa360.pe","password":"Admin12345","nombres":"Ana",
                                 "apellidos":"Ruiz","dni":"10000001"}"""))
                .andReturn();
        assertEquals(201, resultado.getResponse().getStatus());
        return tokenDe(resultado.getResponse().getContentAsString());
    }

    /** Da de alta un estudiante (como ADMIN) y devuelve su token, para probar RBAC. */
    private String crearYTokenearEstudiante() throws Exception {
        return altaYToken("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"alu3@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"30000003"},
                 "perfil":{"fechaNacimiento":"2011-03-03"}}""", "alu3@educa360.pe", "Alumno1234");
    }

    /** Cuerpo de alta de docente: {"usuario": {...}, "perfil": {...}}. */
    private String docenteBody(String email, String dni) {
        return """
                {"usuario":{"email":"%s","password":"Docente1234","nombres":"Dana","apellidos":"Perez",
                            "dni":"%s","telefonos":["987654321"]},
                 "perfil":{"titulo":"Lic. en Matematicas"}}
                """.formatted(email, dni);
    }

    private int login(String email, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn();
        return resultado.getResponse().getStatus();
    }

    /** Login que exige éxito y devuelve el JWT. */
    private String loginYToken(String email, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn();
        assertEquals(200, resultado.getResponse().getStatus(),
                "login inesperado: " + resultado.getResponse().getContentAsString());
        return tokenDe(resultado.getResponse().getContentAsString());
    }

    private long idDe(MvcResult resultado) throws Exception {
        assertEquals(201, resultado.getResponse().getStatus(),
                "creación inesperada: " + resultado.getResponse().getContentAsString());
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(resultado.getResponse().getContentAsString());
        assertTrue(matcher.find(), "la respuesta debe traer el id");
        return Long.parseLong(matcher.group(1));
    }

    private String tokenDe(String cuerpo) {
        Matcher matcher = TOKEN_PATTERN.matcher(cuerpo);
        assertNotNull(matcher.find(), "debe venir el token");
        return matcher.group(1);
    }
}
