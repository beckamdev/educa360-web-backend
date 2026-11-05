package com.educa360.backend;

import com.educa360.backend.entity.Calificacion;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.CalificacionRepository;
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

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Tests del Módulo 6: calificaciones por unidad y criterio.
 *
 * Reglas que protege el suite:
 * - sólo el docente del curso registra, corrige y elimina;
 * - sólo se califica en un bimestre ABIERTO;
 * - la matrícula debe ser de la sección del curso;
 * - la nota es vigesimal con dos decimales;
 * - el alumno y el apoderado sólo ven unidades PUBLICADA;
 * - el upsert es parcial y no borra lo que no aparece;
 * - promedios de unidad, bimestre, año y acta;
 * - con notas no se despublica/elimina la unidad ni se elimina el curso.
 *
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CalificacionApiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\":(\\d+)");

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
    private UnidadRepository unidadRepository;

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
    private CalificacionRepository calificacionRepository;

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        calificacionRepository.deleteAll();
        matriculaRepository.deleteAll();
        vinculoRepository.deleteAll();
        horarioRepository.deleteAll();
        unidadRepository.deleteAll();
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
    @DisplayName("Flujo: docente califica, publica y alumno, apoderado, secretaría y admin consultan")
    void flujoDeCalificaciones() throws Exception {
        Escenario e = prepararEscenario();
        long beto = crearEstudiante("beto@educa360.pe", "30000002", "Beto");
        long matriculaBeto = matricular(beto, e.seccion(), LocalDate.of(2026, 1, 1));
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, e.alumno(), "MADRE", true));

        long unidad = crearUnidad(e.tokenDocente(), e.curso(), e.bimestre(), "Fracciones");

        // Antes de registrar, el alumno no ve nada.
        String tokenAlma = loginYToken("alma@educa360.pe", "Alumno1234");
        String antes = conToken(get("/api/v1/estudiante/calificaciones"), tokenAlma)
                .getResponse().getContentAsString();
        assertTrue(antes.contains("\"detalle\":[]"), antes);
        assertTrue(antes.contains("\"promedioGeneral\":null"), antes);

        // Registro inicial: Alma 16 y 14, Beto 10
        MvcResult registro = registrarNotas(e.tokenDocente(), unidad, """
                [{"matriculaId":%d,"criterio":"Examen parcial","nota":16.00},
                 {"matriculaId":%d,"criterio":"Práctica 1","nota":14.00},
                 {"matriculaId":%d,"criterio":"Examen parcial","nota":10.00}]
                """.formatted(e.matricula(), e.matricula(), matriculaBeto));
        assertEquals(200, registro.getResponse().getStatus(),
                registro.getResponse().getContentAsString());
        String hoja = registro.getResponse().getContentAsString();
        assertTrue(hoja.contains("\"criterio\":\"Examen parcial\""), hoja);
        assertTrue(hoja.contains("\"nota\":16.00"), hoja);
        assertTrue(hoja.contains("\"nota\":10.00"), hoja);

        // La unidad sigue en BORRADOR: el alumno no ve las notas, secretaría sí.
        String borradorAlumno = conToken(get("/api/v1/estudiante/calificaciones"), tokenAlma)
                .getResponse().getContentAsString();
        assertTrue(borradorAlumno.contains("\"detalle\":[]"), borradorAlumno);
        String borradorSecretaria = conToken(get("/api/v1/secretaria/estudiantes/" + e.alumno()
                + "/calificaciones"), admin).getResponse().getContentAsString();
        assertTrue(borradorSecretaria.contains("\"nota\":16.00"), borradorSecretaria);

        // Publicar la unidad
        assertEquals(200, conToken(patch(DOCENTE + "/unidades/" + unidad + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"PUBLICADA\"}"), e.tokenDocente())
                .getResponse().getStatus());

        // Alma: (16 + 14) / 2 = 15.00
        String resumenAlma = conToken(get("/api/v1/estudiante/calificaciones"), tokenAlma)
                .getResponse().getContentAsString();
        assertTrue(resumenAlma.contains("\"promediosUnidad\""), resumenAlma);
        assertTrue(resumenAlma.contains("\"promedio\":15.00"), resumenAlma);
        assertTrue(resumenAlma.contains("\"promedioGeneral\":15.00"), resumenAlma);
        assertTrue(resumenAlma.contains("\"bimestreNombre\":\"Bimestre I\""), resumenAlma);

        // Beto: 10.00
        String tokenBeto = loginYToken("beto@educa360.pe", "Alumno1234");
        String resumenBeto = conToken(get("/api/v1/estudiante/calificaciones"), tokenBeto)
                .getResponse().getContentAsString();
        assertTrue(resumenBeto.contains("\"promedioGeneral\":10.00"), resumenBeto);

        // Apoderado de Alma
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");
        String delHijo = conToken(get("/api/v1/apoderado/hijos/" + e.alumno()
                + "/calificaciones"), tokenMama).getResponse().getContentAsString();
        assertTrue(delHijo.contains("\"promedioGeneral\":15.00"), delHijo);
        assertEquals(404, conToken(get("/api/v1/apoderado/hijos/" + beto
                + "/calificaciones"), tokenMama).getResponse().getStatus(),
                "un apoderado no consulta un hijo no vinculado");

        // Secretaría y ADMIN leen
        assertEquals(200, conToken(get("/api/v1/secretaria/estudiantes/" + e.alumno()
                + "/calificaciones"), admin).getResponse().getStatus());
        assertEquals(200, conToken(get("/api/v1/admin/estudiantes/" + e.alumno()
                + "/calificaciones"), admin).getResponse().getStatus());

        // Acta del docente: Alma 15.00, Beto 10.00, curso 12.50
        String acta = conToken(get(DOCENTE + "/cursos/" + e.curso() + "/actas?bimestreId="
                + e.bimestre()), e.tokenDocente()).getResponse().getContentAsString();
        assertTrue(acta.contains("\"promedioBimestre\":15.00"), acta);
        assertTrue(acta.contains("\"promedioBimestre\":10.00"), acta);
        assertTrue(acta.contains("\"promedioCurso\":12.50"), acta);
        assertEquals(200, conToken(get("/api/v1/admin/cursos/" + e.curso()
                + "/actas?bimestreId=" + e.bimestre()), admin).getResponse().getStatus());

        // Upsert parcial: Alma sube el examen a 20 y agrega Tarea 1; Práctica 1 sigue en 14
        assertEquals(200, registrarNotas(e.tokenDocente(), unidad, """
                [{"matriculaId":%d,"criterio":"Examen parcial","nota":20.00},
                 {"matriculaId":%d,"criterio":"Tarea 1","nota":10.00}]
                """.formatted(e.matricula(), e.matricula())).getResponse().getStatus());
        String actualizada = conToken(get("/api/v1/estudiante/calificaciones"), tokenAlma)
                .getResponse().getContentAsString();
        assertTrue(actualizada.contains("\"nota\":20.00"), actualizada);
        assertTrue(actualizada.contains("\"nota\":14.00"), actualizada);
        // (20 + 14 + 10) / 3 = 14.67
        assertTrue(actualizada.contains("\"promedioGeneral\":14.67"), actualizada);

        // Baja de la nota de Beto: el acta lo deja sin promedio
        Calificacion deBeto = calificacionRepository.findAll().stream()
                .filter(c -> c.getMatricula().getId().equals(matriculaBeto))
                .findFirst().orElseThrow();
        assertEquals(204, conToken(delete(DOCENTE + "/calificaciones/" + deBeto.getId()),
                e.tokenDocente()).getResponse().getStatus());
        String actaSinBeto = conToken(get(DOCENTE + "/cursos/" + e.curso()
                + "/actas?bimestreId=" + e.bimestre()), e.tokenDocente())
                .getResponse().getContentAsString();
        assertTrue(actaSinBeto.contains("\"promedioBimestre\":null"), actaSinBeto);

        // Con notas no se despublica ni se elimina la unidad, ni se elimina el curso
        assertEquals(409, conToken(patch(DOCENTE + "/unidades/" + unidad + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"BORRADOR\"}"), e.tokenDocente())
                .getResponse().getStatus());
        assertEquals(409, conToken(delete(DOCENTE + "/unidades/" + unidad), e.tokenDocente())
                .getResponse().getStatus());
        assertEquals(409, conToken(delete(CURSOS + "/" + e.curso()), admin)
                .getResponse().getStatus());

        // RBAC
        assertEquals(403, conToken(get(DOCENTE + "/unidades/" + unidad + "/calificaciones"),
                tokenAlma).getResponse().getStatus(), "un alumno no entra a la zona docente");
        assertEquals(403, conToken(get("/api/v1/estudiante/calificaciones"), tokenMama)
                .getResponse().getStatus(), "un apoderado no consulta como estudiante");
        assertEquals(403, conToken(get("/api/v1/secretaria/estudiantes/" + e.alumno()
                + "/calificaciones"), tokenAlma).getResponse().getStatus(),
                "un alumno no consulta la zona de secretaría");
    }

    @Test
    @DisplayName("Rechaza notas fuera de rango, con tres decimales, de otra sección o repetidas")
    void validacionesDeNotaYMatricula() throws Exception {
        Escenario e = prepararEscenario();
        long unidad = crearUnidad(e.tokenDocente(), e.curso(), e.bimestre(), "Fracciones");

        assertEquals(400, registrarNotas(e.tokenDocente(), unidad,
                "[{\"matriculaId\":%d,\"criterio\":\"Examen\",\"nota\":20.01}]"
                        .formatted(e.matricula())).getResponse().getStatus(),
                "la nota máxima es 20.00");

        assertEquals(400, registrarNotas(e.tokenDocente(), unidad,
                "[{\"matriculaId\":%d,\"criterio\":\"Examen\",\"nota\":15.555}]"
                        .formatted(e.matricula())).getResponse().getStatus(),
                "la nota admite dos decimales");

        long seccionB = nuevaSeccion(3, "B", e.anio());
        long beto = crearEstudiante("beto@educa360.pe", "30000002", "Beto");
        long matriculaB = matricular(beto, seccionB, LocalDate.of(2026, 1, 1));
        assertEquals(404, registrarNotas(e.tokenDocente(), unidad,
                "[{\"matriculaId\":%d,\"criterio\":\"Examen\",\"nota\":15.00}]"
                        .formatted(matriculaB)).getResponse().getStatus(),
                "la matrícula es de otra sección");

        assertEquals(400, registrarNotas(e.tokenDocente(), unidad, """
                [{"matriculaId":%d,"criterio":"Examen","nota":10.00},
                 {"matriculaId":%d,"criterio":" Examen ","nota":12.00}]
                """.formatted(e.matricula(), e.matricula())).getResponse().getStatus(),
                "la lista no puede repetir matrícula y criterio");

        // Deja una nota y cierra el bimestre
        assertEquals(200, registrarNotas(e.tokenDocente(), unidad,
                "[{\"matriculaId\":%d,\"criterio\":\"Examen\",\"nota\":15.00}]"
                        .formatted(e.matricula())).getResponse().getStatus());
        assertEquals(200, conToken(patch("/api/v1/admin/bimestres/" + e.bimestre() + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"CERRADO\"}"), admin).getResponse().getStatus());

        assertEquals(409, registrarNotas(e.tokenDocente(), unidad,
                "[{\"matriculaId\":%d,\"criterio\":\"Examen\",\"nota\":16.00}]"
                        .formatted(e.matricula())).getResponse().getStatus(),
                "un bimestre cerrado no admite notas");

        Calificacion nota = calificacionRepository.findAll().get(0);
        assertEquals(409, conToken(delete(DOCENTE + "/calificaciones/" + nota.getId()),
                e.tokenDocente()).getResponse().getStatus(),
                "un bimestre cerrado no admite bajas");

        // La lectura sigue disponible
        assertEquals(200, conToken(get("/api/v1/estudiante/calificaciones"),
                loginYToken("alma@educa360.pe", "Alumno1234")).getResponse().getStatus());
    }

    @Test
    @DisplayName("Un docente no ve ni califica unidades de un curso ajeno")
    void docenteNoAccedeACursoAjeno() throws Exception {
        Escenario e = prepararEscenario();
        long unidad = crearUnidad(e.tokenDocente(), e.curso(), e.bimestre(), "Fracciones");

        long otroDocente = crearDocente("otto@educa360.pe", "20000002");
        long otraMateria = crearMateria("Comunicación");
        assertEquals(200, habilitar(otroDocente, "[" + otraMateria + "]"));
        long otroCurso = nuevoCurso(otroDocente, otraMateria, e.seccion());

        String tokenOtto = loginYToken("otto@educa360.pe", "Docente1234");
        assertEquals(404, conToken(get(DOCENTE + "/unidades/" + unidad + "/calificaciones"),
                tokenOtto).getResponse().getStatus());
        assertEquals(404, registrarNotas(tokenOtto, unidad,
                "[{\"matriculaId\":%d,\"criterio\":\"Examen\",\"nota\":15.00}]"
                        .formatted(e.matricula())).getResponse().getStatus());
        assertEquals(404, conToken(get(DOCENTE + "/cursos/" + e.curso() + "/actas?bimestreId="
                + e.bimestre()), tokenOtto).getResponse().getStatus());
        assertNotNull(otroCurso);
    }

    // ==================== Escenario ====================

    private record Escenario(long anio, long bimestre, long seccion, long docente,
                             long materia, long curso, long alumno, long matricula,
                             String tokenDocente) {
    }

    private Escenario prepararEscenario() throws Exception {
        int anio = 2026;
        long anioId = crearAnio(anio, "Año Escolar " + anio, "2026-01-01");
        long bimestre = crearBimestre(anioId, 1, "2026-01-01", "2026-12-31");
        long seccion = nuevaSeccion(3, "A", anioId);
        long docente = crearDocente("dana@educa360.pe", "20000001");
        long materia = crearMateria("Matemática");
        assertEquals(200, habilitar(docente, "[" + materia + "]"));
        long curso = nuevoCurso(docente, materia, seccion);

        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long matricula = matricular(alumno, seccion, LocalDate.of(2026, 1, 1));

        assertEquals(200, abrirAnio(anioId));
        assertEquals(200, abrirBimestre(bimestre));

        String tokenDocente = loginYToken("dana@educa360.pe", "Docente1234");
        return new Escenario(anioId, bimestre, seccion, docente, materia, curso,
                alumno, matricula, tokenDocente);
    }

    // ==================== Helpers de API ====================

    private MvcResult crear(String url, String body) throws Exception {
        return conToken(post(url).contentType(MediaType.APPLICATION_JSON).content(body), admin);
    }

    private MvcResult conToken(MockHttpServletRequestBuilder peticion, String token)
            throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult registrarNotas(String token, long unidadId, String calificacionesJson)
            throws Exception {
        return conToken(put(DOCENTE + "/unidades/" + unidadId + "/calificaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificaciones\":" + calificacionesJson + "}"), token);
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

    private long crearBimestre(long anioId, int numero, String inicio, String fin)
            throws Exception {
        return idDe(crear(ANIOS + "/" + anioId + "/bimestres", """
                {"numero":%d,"fechaInicio":"%s","fechaFin":"%s"}"""
                .formatted(numero, inicio, fin)));
    }

    private int abrirBimestre(long id) throws Exception {
        return conToken(patch("/api/v1/admin/bimestres/" + id + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ABIERTO\"}"), admin)
                .getResponse().getStatus();
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

    private long crearUnidad(String tokenDocente, long cursoId, long bimestreId, String titulo)
            throws Exception {
        MvcResult resultado = conToken(post(DOCENTE + "/cursos/" + cursoId + "/unidades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bimestreId\":%d,\"titulo\":\"%s\"}"
                        .formatted(bimestreId, titulo)), tokenDocente);
        assertEquals(201, resultado.getResponse().getStatus(),
                resultado.getResponse().getContentAsString());
        return idDe(resultado);
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

    private long matricular(long estudianteId, long seccionId, LocalDate fecha) throws Exception {
        return idDe(crear(MATRICULAS, """
                {"estudianteId":%d,"seccionId":%d,"fechaMatricula":"%s"}"""
                .formatted(estudianteId, seccionId, fecha)));
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
        Matcher matcher = ID_PATTERN.matcher(resultado.getResponse().getContentAsString());
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
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(email, password)))
                .andReturn();
        assertEquals(200, resultado.getResponse().getStatus());
        return tokenDe(resultado.getResponse().getContentAsString());
    }
}
