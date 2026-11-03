package com.educa360.backend;

import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.ContenidoRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.HorarioBloqueRepository;
import com.educa360.backend.repository.MateriaRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.SeccionRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.UnidadRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Tests del Módulo 4: planificación docente (unidades y contenidos).
 *
 * Reglas que protege el suite:
 * - el docente sólo planifica sus cursos (un curso ajeno responde 404);
 * - la unidad nace BORRADOR y con número automático;
 * - el contenido nace al final y se puede reordenar sin violar el unique;
 * - estudiante y apoderado sólo ven unidades PUBLICADA;
 * - ADMIN puede leer, incluso borradores;
 * - un bimestre CERRADO no admite planificación.
 *
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PlanificacionApiTest {

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
    private static final String DOCENTE = "/api/v1/docente";

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
    private UnidadRepository unidadRepository;

    @Autowired
    private ContenidoRepository contenidoRepository;

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        // Orden de borrado por las FK: lo hijo primero
        contenidoRepository.deleteAll();
        unidadRepository.deleteAll();
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
    @DisplayName("Flujo: docente planifica, publica y alumno/apoderado ven la unidad")
    void docentePublicaYFamiliasVenLaUnidad() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long bimestre = crearBimestre(anio, 1, "2026-03-02", "2026-05-15");
        long seccion = nuevaSeccion(3, "A", anio);
        long docente = crearDocente("dana@educa360.pe", "20000001");
        long materia = crearMateria("Matemática");
        assertEquals(200, habilitar(docente, "[" + materia + "]"));
        long curso = nuevoCurso(docente, materia, seccion);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        assertEquals(201, matricular(alumno, seccion));
        assertEquals(200, abrirAnio(anio));

        String tokenDocente = loginYToken("dana@educa360.pe", "Docente1234");
        long unidad = crearUnidad(tokenDocente, curso, bimestre, "Unidad 1",
                "2026-03-03", "2026-04-01");
        long contenidoA = crearContenido(tokenDocente, unidad, "Tema A", "2026-03-05");
        long contenidoB = crearContenido(tokenDocente, unidad, "Tema B", "2026-03-12");
        long borrador = crearUnidad(tokenDocente, curso, bimestre, "Unidad borrador",
                null, null);

        MvcResult reorden = conToken(put(DOCENTE + "/unidades/" + unidad + "/contenidos/orden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contenidoIds\":[%d,%d]}".formatted(contenidoB, contenidoA)),
                tokenDocente);
        assertEquals(200, reorden.getResponse().getStatus());
        String ordenado = reorden.getResponse().getContentAsString();
        assertTrue(ordenado.indexOf("Tema B") < ordenado.indexOf("Tema A"),
                "el reordenamiento debe respetar la lista: " + ordenado);

        String tokenAlumno = loginYToken("alma@educa360.pe", "Alumno1234");
        MvcResult antes = conToken(get("/api/v1/estudiante/cursos/" + curso + "/unidades"),
                tokenAlumno);
        assertEquals(200, antes.getResponse().getStatus());
        assertFalse(antes.getResponse().getContentAsString().contains("Unidad 1"),
                "un borrador no se publica");
        assertFalse(antes.getResponse().getContentAsString().contains("Unidad borrador"));

        MvcResult publicada = conToken(patch(DOCENTE + "/unidades/" + unidad + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PUBLICADA\"}"), tokenDocente);
        assertEquals(200, publicada.getResponse().getStatus());
        assertTrue(publicada.getResponse().getContentAsString().contains("\"estado\":\"PUBLICADA\""));

        String visibles = conToken(get("/api/v1/estudiante/cursos/" + curso + "/unidades"),
                tokenAlumno).getResponse().getContentAsString();
        assertTrue(visibles.contains("Unidad 1"), visibles);
        assertTrue(visibles.contains("Tema B"), visibles);
        assertTrue(visibles.indexOf("Tema B") < visibles.indexOf("Tema A"), visibles);
        assertFalse(visibles.contains("Unidad borrador"), "el otro borrador sigue oculto");

        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, alumno, "MADRE", true));
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");
        String delHijo = conToken(get("/api/v1/apoderado/hijos/" + alumno
                + "/cursos/" + curso + "/unidades"), tokenMama).getResponse().getContentAsString();
        assertTrue(delHijo.contains("Unidad 1"), delHijo);
        assertFalse(delHijo.contains("Unidad borrador"), delHijo);

        // ADMIN lee todo, incluidos los borradores
        String delAdmin = conToken(get("/api/v1/admin/cursos/" + curso + "/unidades"), admin)
                .getResponse().getContentAsString();
        assertTrue(delAdmin.contains("Unidad 1"), delAdmin);
        assertTrue(delAdmin.contains("Unidad borrador"), delAdmin);
        assertEquals(200, conToken(get("/api/v1/admin/unidades/" + borrador), admin)
                .getResponse().getStatus());

        // El alumno no puede planificar; el apoderado tampoco
        assertEquals(403, conToken(post(DOCENTE + "/cursos/" + curso + "/unidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bimestreId\":%d,\"titulo\":\"No\"}".formatted(bimestre)),
                tokenAlumno).getResponse().getStatus());
        assertEquals(403, conToken(get(DOCENTE + "/cursos/" + curso + "/unidades"), tokenMama)
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("Un docente no ve la planificación de un curso ajeno")
    void docenteNoVeCursoAjeno() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccionA = nuevaSeccion(3, "A", anio);
        long seccionB = nuevaSeccion(3, "B", anio);
        long docenteA = crearDocente("dana@educa360.pe", "20000001");
        long docenteB = crearDocente("otto@educa360.pe", "20000002");
        long materia = crearMateria("Matemática");
        assertEquals(200, habilitar(docenteA, "[" + materia + "]"));
        assertEquals(200, habilitar(docenteB, "[" + materia + "]"));
        long cursoA = nuevoCurso(docenteA, materia, seccionA);
        long cursoB = nuevoCurso(docenteB, materia, seccionB);

        String tokenA = loginYToken("dana@educa360.pe", "Docente1234");
        assertEquals(200, conToken(get(DOCENTE + "/cursos/" + cursoA + "/unidades"), tokenA)
                .getResponse().getStatus());
        assertEquals(404, conToken(get(DOCENTE + "/cursos/" + cursoB + "/unidades"), tokenA)
                .getResponse().getStatus(), "el curso ajeno no existe para este docente");
        assertEquals(404, conToken(post(DOCENTE + "/cursos/" + cursoB + "/unidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bimestreId\":1,\"titulo\":\"No\"}"), tokenA)
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("Un bimestre CERRADO no admite unidades nuevas")
    void bimestreCerradoNoAdmitePlanificacion() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long bimestre = crearBimestre(anio, 1, "2026-03-02", "2026-05-15");
        long seccion = nuevaSeccion(3, "A", anio);
        long docente = crearDocente("dana@educa360.pe", "20000001");
        long materia = crearMateria("Matemática");
        assertEquals(200, habilitar(docente, "[" + materia + "]"));
        long curso = nuevoCurso(docente, materia, seccion);

        assertEquals(200, conToken(patch("/api/v1/admin/bimestres/" + bimestre + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CERRADO\"}"), admin)
                .getResponse().getStatus());

        String tokenDocente = loginYToken("dana@educa360.pe", "Docente1234");
        assertEquals(409, conToken(post(DOCENTE + "/cursos/" + curso + "/unidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bimestreId\":%d,\"titulo\":\"Unidad\"}".formatted(bimestre)),
                tokenDocente).getResponse().getStatus());
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

    private long crearBimestre(long anioId, int numero, String inicio, String fin) throws Exception {
        return idDe(crear(ANIOS + "/" + anioId + "/bimestres", """
                {"numero":%d,"fechaInicio":"%s","fechaFin":"%s"}"""
                .formatted(numero, inicio, fin)));
    }

    private long nuevaSeccion(int grado, String letra, long anioId) throws Exception {
        return idDe(crear(SECCIONES,
                "{\"grado\":%d,\"letra\":\"%s\",\"aula\":\"A-101\",\"tutorId\":null,\"anioId\":%d}"
                        .formatted(grado, letra, anioId)));
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

    private int matricular(long estudianteId, long seccionId) throws Exception {
        return crear(MATRICULAS, """
                {"estudianteId":%d,"seccionId":%d,"fechaMatricula":"2026-02-20"}"""
                .formatted(estudianteId, seccionId))
                .getResponse().getStatus();
    }

    private int vincular(long apoderadoId, long estudianteId, String parentesco,
                         boolean principal) throws Exception {
        return crear(APODERADOS + "/" + apoderadoId + "/representados", """
                {"estudianteId":%d,"parentesco":"%s","responsablePrincipal":%b}"""
                .formatted(estudianteId, parentesco, principal))
                .getResponse().getStatus();
    }

    private long crearUnidad(String token, long cursoId, long bimestreId, String titulo,
                             String inicio, String fin) throws Exception {
        String fechas = (inicio == null ? "" : ",\"fechaInicio\":\"" + inicio + "\"")
                + (fin == null ? "" : ",\"fechaFin\":\"" + fin + "\"");
        return idDe(conToken(post(DOCENTE + "/cursos/" + cursoId + "/unidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bimestreId\":%d,\"titulo\":\"%s\"%s}"
                                .formatted(bimestreId, titulo, fechas)), token));
    }

    private long crearContenido(String token, long unidadId, String titulo,
                                String fechaPrevista) throws Exception {
        return idDe(conToken(post(DOCENTE + "/unidades/" + unidadId + "/contenidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"%s\",\"fechaPrevista\":\"%s\"}"
                                .formatted(titulo, fechaPrevista)), token));
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
