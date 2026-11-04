package com.educa360.backend;

import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.ComunicadoRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.HorarioBloqueRepository;
import com.educa360.backend.repository.MateriaRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.SeccionRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Tests del Módulo 9: comunicados.
 *
 * Reglas que protege el suite:
 * - ADMIN publica generales o por sección y gestiona cualquiera;
 * - SECRETARÍA publica solo generales y gestiona solo los suyos;
 * - DOCENTE publica en secciones donde dicta clase o es tutor, solo los suyos;
 * - alumno y apoderado ven generales y los de su sección;
 * - un año CERRADO es solo lectura;
 * - la baja es lógica y el contenido tiene límite.
 *
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ComunicadoApiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

    private static final String ANIOS = "/api/v1/admin/anios";
    private static final String SECCIONES = "/api/v1/admin/secciones";
    private static final String DOCENTES = "/api/v1/admin/docentes";
    private static final String MATERIAS = "/api/v1/admin/materias";
    private static final String CURSOS = "/api/v1/admin/cursos";
    private static final String ESTUDIANTES = "/api/v1/secretaria/estudiantes";
    private static final String APODERADOS = "/api/v1/secretaria/apoderados";
    private static final String MATRICULAS = "/api/v1/secretaria/matriculas";

    private static final String ADMIN_COM = "/api/v1/admin/comunicados";
    private static final String SECRETARIA_COM = "/api/v1/secretaria/comunicados";
    private static final String DOCENTE_COM = "/api/v1/docente/comunicados";
    private static final String ESTUDIANTE_COM = "/api/v1/estudiante/comunicados";
    private static final String APODERADO_COM = "/api/v1/apoderado/comunicados";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocenteRepository docenteRepository;

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private AnioEscolarRepository anioRepository;

    @Autowired
    private BimestreRepository bimestreRepository;

    @Autowired
    private SeccionRepository seccionRepository;

    @Autowired
    private CursoRepository cursoRepository;

    @Autowired
    private HorarioBloqueRepository horarioRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private SecretariaRepository secretariaRepository;

    @Autowired
    private ApoderadoRepository apoderadoRepository;

    @Autowired
    private ApoderadoEstudianteRepository vinculoRepository;

    @Autowired
    private MatriculaRepository matriculaRepository;

    @Autowired
    private ComunicadoRepository comunicadoRepository;

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        // Orden de borrado por las FK: lo hijo primero
        comunicadoRepository.deleteAll();
        matriculaRepository.deleteAll();
        vinculoRepository.deleteAll();
        horarioRepository.deleteAll();
        cursoRepository.deleteAll();
        seccionRepository.deleteAll();
        docenteRepository.deleteAll();
        materiaRepository.deleteAll();
        bimestreRepository.deleteAll();
        anioRepository.deleteAll();
        estudianteRepository.deleteAll();
        secretariaRepository.deleteAll();
        apoderadoRepository.deleteAll();
        userRepository.deleteAll();

        admin = registrarAdmin();
    }

    @Test
    @DisplayName("ADMIN publica general y por sección; cada rol ve lo que le corresponde")
    void adminPublicaGeneralYDeSeccion() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccionA = nuevaSeccion(3, "A", anio, null);
        long seccionB = nuevaSeccion(3, "B", anio, null);
        long docente = crearDocente("dana@educa360.pe", "20000001");
        long materia = crearMateria("Matemática");
        assertEquals(200, habilitar(docente, "[" + materia + "]"));
        nuevoCurso(docente, materia, seccionA);

        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        assertEquals(201, matricular(alumno, seccionA, "2026-02-20"));
        assertEquals(200, abrirAnio(anio));

        long general = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":null,"contenido":"Bienvenidos al año"}"""
                .formatted(anio)));
        long deA = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":%d,"contenido":"Solo 3°A"}"""
                .formatted(anio, seccionA)));
        long deB = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":%d,"contenido":"Solo 3°B"}"""
                .formatted(anio, seccionB)));

        // ADMIN lista todo y puede filtrar.
        String todos = conToken(get(ADMIN_COM), admin).getResponse().getContentAsString();
        assertTrue(todos.contains("Bienvenidos al año"), todos);
        assertTrue(todos.contains("Solo 3°A"), todos);
        assertTrue(todos.contains("Solo 3°B"), todos);

        String soloA = conToken(get(ADMIN_COM + "?seccionId=" + seccionA), admin)
                .getResponse().getContentAsString();
        assertTrue(soloA.contains("Solo 3°A"), soloA);
        assertFalse(soloA.contains("Solo 3°B"), soloA);

        // DOCENTE lee generales + su sección, no la ajena.
        String tokenDocente = loginYToken("dana@educa360.pe", "Docente1234");
        String delDocente = conToken(get(DOCENTE_COM), tokenDocente)
                .getResponse().getContentAsString();
        assertTrue(delDocente.contains("\"id\":" + general), delDocente);
        assertTrue(delDocente.contains("\"id\":" + deA), delDocente);
        assertFalse(delDocente.contains("\"id\":" + deB), delDocente);

        // ESTUDIANTE ve generales + su sección.
        String tokenAlumno = loginYToken("alma@educa360.pe", "Alumno1234");
        String delAlumno = conToken(get(ESTUDIANTE_COM), tokenAlumno)
                .getResponse().getContentAsString();
        assertTrue(delAlumno.contains("\"id\":" + general), delAlumno);
        assertTrue(delAlumno.contains("\"id\":" + deA), delAlumno);
        assertFalse(delAlumno.contains("\"id\":" + deB), delAlumno);
    }

    @Test
    @DisplayName("SECRETARÍA publica y gestiona solo comunicados generales propios")
    void secretariaSoloPublicaGenerales() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio, null);
        assertEquals(200, abrirAnio(anio));
        crearSecretaria("sofia@educa360.pe", "21000001");
        String tokenSecretaria = loginYToken("sofia@educa360.pe", "Secreta1234");

        long general = idDe(conToken(post(SECRETARIA_COM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":null,"contenido":"Aviso general"}"""
                                .formatted(anio)), tokenSecretaria));

        assertEquals(400, conToken(post(SECRETARIA_COM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":%d,"contenido":"No permitido"}"""
                                .formatted(anio, seccion)), tokenSecretaria)
                .getResponse().getStatus());

        String listado = conToken(get(SECRETARIA_COM), tokenSecretaria)
                .getResponse().getContentAsString();
        assertTrue(listado.contains("\"id\":" + general), listado);

        // La secretaría edita su propio mensaje.
        assertEquals(200, conToken(put(SECRETARIA_COM + "/" + general)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seccionId\":null,\"contenido\":\"Aviso corregido\"}"),
                tokenSecretaria).getResponse().getStatus());

        // Un comunicado de ADMIN no es suyo: 404 al intentar editarlo.
        long deAdmin = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":null,"contenido":"De admin"}"""
                .formatted(anio)));
        assertEquals(404, conToken(put(SECRETARIA_COM + "/" + deAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seccionId\":null,\"contenido\":\"Intento\"}"),
                tokenSecretaria).getResponse().getStatus());
        assertEquals(404, conToken(delete(SECRETARIA_COM + "/" + deAdmin), tokenSecretaria)
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("DOCENTE publica en secciones donde dicta clase o es tutor, no en ajenas")
    void docentePublicaEnSusSecciones() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long docenteA = crearDocente("dana@educa360.pe", "20000001");
        long docenteB = crearDocente("otto@educa360.pe", "20000002");
        long seccionA = nuevaSeccion(3, "A", anio, null);
        long seccionB = nuevaSeccion(3, "B", anio, docenteB);
        long materia = crearMateria("Matemática");
        assertEquals(200, habilitar(docenteA, "[" + materia + "]"));
        assertEquals(200, habilitar(docenteB, "[" + materia + "]"));
        nuevoCurso(docenteA, materia, seccionA);
        assertEquals(200, abrirAnio(anio));

        String tokenA = loginYToken("dana@educa360.pe", "Docente1234");
        String tokenB = loginYToken("otto@educa360.pe", "Docente1234");

        // Dana dicta en A: puede publicar ahí.
        long deA = idDe(conToken(post(DOCENTE_COM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":%d,"contenido":"Para mi curso"}"""
                                .formatted(anio, seccionA)), tokenA));

        // Dana no dicta ni es tutora de B: 404.
        assertEquals(404, conToken(post(DOCENTE_COM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":%d,"contenido":"No es mía"}"""
                                .formatted(anio, seccionB)), tokenA)
                .getResponse().getStatus());

        // Otto es tutor de B sin dictar clase: puede publicar ahí.
        long deB = idDe(conToken(post(DOCENTE_COM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":%d,"contenido":"Desde tutoría"}"""
                                .formatted(anio, seccionB)), tokenB));

        // Otto no puede eliminar el comunicado de Dana.
        assertEquals(404, conToken(delete(DOCENTE_COM + "/" + deA), tokenB)
                .getResponse().getStatus());

        // Dana sí puede eliminar el suyo.
        assertEquals(204, conToken(delete(DOCENTE_COM + "/" + deA), tokenA)
                .getResponse().getStatus());
        assertEquals(404, conToken(delete(DOCENTE_COM + "/" + deA), tokenA)
                .getResponse().getStatus());

        // El comunicado de Otto sigue visible para él.
        String listado = conToken(get(DOCENTE_COM), tokenB).getResponse().getContentAsString();
        assertTrue(listado.contains("\"id\":" + deB), listado);
    }

    @Test
    @DisplayName("El apoderado ve generales y las secciones de sus hijos activos")
    void apoderadoVeComunicadosDeSusHijos() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccionA = nuevaSeccion(3, "A", anio, null);
        long seccionB = nuevaSeccion(3, "B", anio, null);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        assertEquals(201, matricular(alumno, seccionA, "2026-02-20"));
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, alumno, "MADRE", true));
        assertEquals(200, abrirAnio(anio));

        long general = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":null,"contenido":"General"}"""
                .formatted(anio)));
        long deA = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":%d,"contenido":"Para 3°A"}"""
                .formatted(anio, seccionA)));
        long deB = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":%d,"contenido":"Para 3°B"}"""
                .formatted(anio, seccionB)));

        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");
        String visibles = conToken(get(APODERADO_COM), tokenMama)
                .getResponse().getContentAsString();
        assertTrue(visibles.contains("\"id\":" + general), visibles);
        assertTrue(visibles.contains("\"id\":" + deA), visibles);
        assertFalse(visibles.contains("\"id\":" + deB), visibles);
    }

    @Test
    @DisplayName("El alumno y el apoderado no pueden publicar; el docente no accede a admin")
    void rbacDeComunicados() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio, null);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        assertEquals(201, matricular(alumno, seccion, "2026-02-20"));
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, alumno, "MADRE", true));
        long docente = crearDocente("dana@educa360.pe", "20000001");
        assertEquals(200, abrirAnio(anio));

        String tokenAlumno = loginYToken("alma@educa360.pe", "Alumno1234");
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");
        String tokenDocente = loginYToken("dana@educa360.pe", "Docente1234");

        String cuerpo = """
                {"anioId":%d,"seccionId":null,"contenido":"Intento"}"""
                .formatted(anio);

        assertEquals(403, conToken(post(ADMIN_COM)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo), tokenAlumno)
                .getResponse().getStatus());
        assertEquals(403, conToken(post(DOCENTE_COM)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo), tokenAlumno)
                .getResponse().getStatus());
        assertEquals(403, conToken(get(SECRETARIA_COM), tokenMama)
                .getResponse().getStatus());
        assertEquals(403, conToken(post(ADMIN_COM)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo), tokenDocente)
                .getResponse().getStatus());
        assertNotNull(docente);
    }

    @Test
    @DisplayName("Un año CERRADO queda en solo lectura para comunicados")
    void anioCerradoEsSoloLectura() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        assertEquals(200, abrirAnio(anio));
        long comunicado = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":null,"contenido":"Antes del cierre"}"""
                .formatted(anio)));

        assertEquals(200, conToken(patch(ANIOS + "/" + anio + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CERRADO\"}"), admin)
                .getResponse().getStatus());

        assertEquals(409, conToken(post(ADMIN_COM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":null,"contenido":"Tarde"}"""
                                .formatted(anio)), admin)
                .getResponse().getStatus());
        assertEquals(409, conToken(put(ADMIN_COM + "/" + comunicado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seccionId\":null,\"contenido\":\"Editado\"}"), admin)
                .getResponse().getStatus());
        assertEquals(409, conToken(delete(ADMIN_COM + "/" + comunicado), admin)
                .getResponse().getStatus());

        // La lectura sigue disponible.
        String listado = conToken(get(ADMIN_COM), admin).getResponse().getContentAsString();
        assertTrue(listado.contains("Antes del cierre"), listado);
    }

    @Test
    @DisplayName("La baja es lógica: el comunicado desaparece y no se puede volver a tocar")
    void bajaLogica() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        assertEquals(200, abrirAnio(anio));
        long comunicado = idDe(crear(ADMIN_COM, """
                {"anioId":%d,"seccionId":null,"contenido":"Mensaje"}"""
                .formatted(anio)));

        assertEquals(204, conToken(delete(ADMIN_COM + "/" + comunicado), admin)
                .getResponse().getStatus());

        String listado = conToken(get(ADMIN_COM), admin).getResponse().getContentAsString();
        assertFalse(listado.contains("\"id\":" + comunicado), listado);
        assertEquals(404, conToken(put(ADMIN_COM + "/" + comunicado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seccionId\":null,\"contenido\":\"Editado\"}"), admin)
                .getResponse().getStatus());
        assertEquals(404, conToken(delete(ADMIN_COM + "/" + comunicado), admin)
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("El contenido vacío o mayor a 5000 caracteres se rechaza")
    void contenidoInvalido() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        assertEquals(200, abrirAnio(anio));

        assertEquals(400, conToken(post(ADMIN_COM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":null,"contenido":"   "}"""
                                .formatted(anio)), admin)
                .getResponse().getStatus());
        assertEquals(400, conToken(post(ADMIN_COM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anioId":%d,"seccionId":null,"contenido":"%s"}"""
                                .formatted(anio, "x".repeat(5001))), admin)
                .getResponse().getStatus());
    }

    // ==================== Helpers ====================

    private MvcResult crear(String url, String body) throws Exception {
        return conToken(post(url).contentType(MediaType.APPLICATION_JSON).content(body), admin);
    }

    private MvcResult conToken(MockHttpServletRequestBuilder peticion, String token) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
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

    private long crearAnio(int anio, String nombre, String inicio) throws Exception {
        return idDe(crear(ANIOS, """
                {"anio":%d,"nombre":"%s","fechaInicio":"%s"}"""
                .formatted(anio, nombre, inicio)));
    }

    private int abrirAnio(long id) throws Exception {
        return conToken(patch(ANIOS + "/" + id + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ABIERTO\"}"), admin)
                .getResponse().getStatus();
    }

    private long nuevaSeccion(int grado, String letra, long anioId, Long tutorId) throws Exception {
        return idDe(crear(SECCIONES, """
                {"grado":%d,"letra":"%s","aula":"A-101","tutorId":%s,"anioId":%d}"""
                .formatted(grado, letra, tutorId, anioId)));
    }

    private long crearDocente(String email, String dni) throws Exception {
        assertEquals(201, crear(DOCENTES, """
                {"usuario":{"email":"%s","password":"Docente1234","nombres":"Dana","apellidos":"Perez",
                            "dni":"%s"},
                 "perfil":{"titulo":"Lic. en Matematicas"}}
                """.formatted(email, dni)).getResponse().getStatus());
        return docenteRepository.findAll().stream()
                .filter(d -> d.getUser().getEmail().equals(email))
                .findFirst().orElseThrow().getId();
    }

    private long crearMateria(String nombre) throws Exception {
        return idDe(crear(MATERIAS, "{\"nombre\":\"%s\"}".formatted(nombre)));
    }

    private int habilitar(long docenteId, String materiaIdsJson) throws Exception {
        return conToken(put(DOCENTES + "/" + docenteId + "/materias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materiaIds\":" + materiaIdsJson + "}"), admin)
                .getResponse().getStatus();
    }

    private long nuevoCurso(long docenteId, long materiaId, long seccionId) throws Exception {
        return idDe(crear(CURSOS, """
                {"docenteId":%d,"materiaId":%d,"seccionId":%d}"""
                .formatted(docenteId, materiaId, seccionId)));
    }

    private long crearEstudiante(String email, String dni, String nombres) throws Exception {
        assertEquals(201, crear(ESTUDIANTES, """
                {"usuario":{"email":"%s","password":"Alumno1234","nombres":"%s","apellidos":"Luna",
                            "dni":"%s"},
                 "perfil":{"fechaNacimiento":"2011-03-03"}}""".formatted(email, nombres, dni))
                .getResponse().getStatus());
        return estudianteRepository.findAll().stream()
                .filter(e -> e.getUser().getEmail().equals(email))
                .findFirst().orElseThrow().getId();
    }

    private long crearApoderado(String email, String dni, String nombres) throws Exception {
        assertEquals(201, crear(APODERADOS, """
                {"usuario":{"email":"%s","password":"Familia1234","nombres":"%s","apellidos":"Soto",
                            "dni":"%s","telefonos":["999888777"]},
                 "perfil":{}}""".formatted(email, nombres, dni))
                .getResponse().getStatus());
        return apoderadoRepository.findAll().stream()
                .filter(a -> a.getUser().getEmail().equals(email))
                .findFirst().orElseThrow().getId();
    }

    private long crearSecretaria(String email, String dni) throws Exception {
        assertEquals(201, crear("/api/v1/admin/secretarias", """
                {"usuario":{"email":"%s","password":"Secreta1234","nombres":"Sofia",
                            "apellidos":"Quintos","dni":"%s","telefonos":["912345678"]},
                 "perfil":{}}""".formatted(email, dni)).getResponse().getStatus());
        return secretariaRepository.findAll().stream()
                .filter(s -> s.getUser().getEmail().equals(email))
                .findFirst().orElseThrow().getId();
    }

    private int matricular(long estudianteId, long seccionId, String fecha) throws Exception {
        return crear(MATRICULAS, """
                {"estudianteId":%d,"seccionId":%d,"fechaMatricula":"%s"}"""
                .formatted(estudianteId, seccionId, fecha))
                .getResponse().getStatus();
    }

    private int vincular(long apoderadoId, long estudianteId, String parentesco,
                         boolean principal) throws Exception {
        return crear(APODERADOS + "/" + apoderadoId + "/representados", """
                {"estudianteId":%d,"parentesco":"%s","responsablePrincipal":%b}"""
                .formatted(estudianteId, parentesco, principal))
                .getResponse().getStatus();
    }

    private long idDe(MvcResult resultado) throws Exception {
        assertEquals(201, resultado.getResponse().getStatus(),
                "creación inesperada: " + resultado.getResponse().getContentAsString());
        Matcher matcher = Pattern.compile("\"id\":(\\d+)")
                .matcher(resultado.getResponse().getContentAsString());
        assertTrue(matcher.find(), "la respuesta debe traer el id");
        return Long.parseLong(matcher.group(1));
    }

    private String tokenDe(String cuerpo) {
        Matcher matcher = TOKEN_PATTERN.matcher(cuerpo);
        assertNotNull(matcher.find(), "debe venir el token");
        return matcher.group(1);
    }

    private String loginYToken(String email, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn();
        assertEquals(200, resultado.getResponse().getStatus());
        return tokenDe(resultado.getResponse().getContentAsString());
    }
}
