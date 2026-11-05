package com.educa360.backend;

import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.AsistenciaRepository;
import com.educa360.backend.repository.BimestreRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
 * Tests del Módulo 5: asistencia por bloque de horario y fecha.
 *
 * Reglas que protege el suite:
 * - el docente registra por clase y sólo ve sus cursos;
 * - el docente sólo manda "asistió / no asistió"; TARDANZA la deriva el
 *   sistema con la hora del servidor;
 * - no se registra una fecha futura, un día que no es del bloque ni un
 *   bimestre cerrado;
 * - un alumno de otra sección no entra en la hoja;
 * - el porcentaje cuenta PRESENTE/TARDANZA/JUSTIFICADA como efectivas;
 * - con asistencias no se reemplaza el horario ni se elimina el curso;
 * - estudiante, apoderado, secretaría y admin leen el historial.
 *
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AsistenciaApiTest {

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
    private AsistenciaRepository asistenciaRepository;

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        // Orden de borrado por las FK: lo hijo primero
        asistenciaRepository.deleteAll();
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
    @DisplayName("Flujo: docente registra y estudiante, apoderado, secretaría y admin consultan")
    void flujoDeAsistencia() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);

        long alumno2 = crearEstudiante("beto@educa360.pe", "30000002", "Beto");
        long matricula2 = matricular(alumno2, e.seccion(), primerDia(e));
        long alumno3 = crearEstudiante("carla@educa360.pe", "30000003", "Carla");
        long matricula3 = matricular(alumno3, e.seccion(), primerDia(e));

        // Hoja inicial: los tres alumnos, sin estado todavía
        MvcResult hoja = conToken(get(DOCENTE + "/cursos/" + e.curso() + "/asistencia?bloqueId="
                + e.bloque() + "&fecha=" + clase), e.tokenDocente());
        assertEquals(200, hoja.getResponse().getStatus(),
                hoja.getResponse().getContentAsString());
        String inicial = hoja.getResponse().getContentAsString();
        assertTrue(inicial.contains("\"estado\":null"), inicial);
        assertTrue(inicial.contains("\"matriculaId\":" + e.matricula()), inicial);
        assertTrue(inicial.contains("\"matriculaId\":" + matricula2), inicial);
        assertTrue(inicial.contains("\"matriculaId\":" + matricula3), inicial);

        // Upsert parcial: alumno 1 presente, alumno 2 falta, alumno 3 sin tocar
        MvcResult registro = registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), clase,
                ("[{\"matriculaId\":%d,\"asistio\":true},"
                        + "{\"matriculaId\":%d,\"asistio\":false,\"observacion\":\"Sin aviso\"}]")
                        .formatted(e.matricula(), matricula2));
        assertEquals(200, registro.getResponse().getStatus(),
                registro.getResponse().getContentAsString());
        String registrada = registro.getResponse().getContentAsString();
        assertTrue(registrada.contains("\"estado\":\"PRESENTE\""), registrada);
        assertTrue(registrada.contains("\"estado\":\"FALTA\""), registrada);
        assertTrue(registrada.contains("\"estado\":null"), registrada);
        assertTrue(registrada.contains("\"observacion\":\"Sin aviso\""), registrada);

        // Alumno 1: 1 de 1 efectiva = 100.00%
        String token1 = loginYToken("alma@educa360.pe", "Alumno1234");
        String resumen1 = conToken(get("/api/v1/estudiante/asistencias"), token1)
                .getResponse().getContentAsString();
        assertTrue(resumen1.contains("\"total\":1"), resumen1);
        assertTrue(resumen1.contains("\"efectivas\":1"), resumen1);
        assertTrue(resumen1.contains("\"faltas\":0"), resumen1);
        assertTrue(resumen1.contains("\"porcentaje\":100.00"), resumen1);
        assertTrue(resumen1.contains("\"estado\":\"PRESENTE\""), resumen1);

        // Filtros por año y bimestre
        String filtrado = conToken(get("/api/v1/estudiante/asistencias?anioId=" + e.anio()
                + "&bimestreId=" + e.bimestre()), token1).getResponse().getContentAsString();
        assertTrue(filtrado.contains("\"total\":1"), filtrado);
        assertTrue(filtrado.contains("\"bimestreNombre\":\"Bimestre I\""), filtrado);

        // Alumno 2: falta injustificada = 0.00%
        String token2 = loginYToken("beto@educa360.pe", "Alumno1234");
        String resumen2 = conToken(get("/api/v1/estudiante/asistencias"), token2)
                .getResponse().getContentAsString();
        assertTrue(resumen2.contains("\"total\":1"), resumen2);
        assertTrue(resumen2.contains("\"faltas\":1"), resumen2);
        assertTrue(resumen2.contains("\"porcentaje\":0.00"), resumen2);

        // Alumno 3: todavía sin registros
        String token3 = loginYToken("carla@educa360.pe", "Alumno1234");
        String resumen3 = conToken(get("/api/v1/estudiante/asistencias"), token3)
                .getResponse().getContentAsString();
        assertTrue(resumen3.contains("\"total\":0"), resumen3);
        assertTrue(resumen3.contains("\"porcentaje\":0.00"), resumen3);

        // Apoderado del alumno 1
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, e.alumno(), "MADRE", true));
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");
        String delHijo = conToken(get("/api/v1/apoderado/hijos/" + e.alumno()
                + "/asistencias"), tokenMama).getResponse().getContentAsString();
        assertTrue(delHijo.contains("\"porcentaje\":100.00"), delHijo);
        assertEquals(404, conToken(get("/api/v1/apoderado/hijos/" + alumno2
                + "/asistencias"), tokenMama).getResponse().getStatus(),
                "un apoderado no consulta un hijo no vinculado");

        // Secretaría y ADMIN leen el mismo historial
        assertEquals(200, conToken(get("/api/v1/secretaria/estudiantes/" + e.alumno()
                + "/asistencias"), admin).getResponse().getStatus());
        assertEquals(200, conToken(get("/api/v1/admin/estudiantes/" + e.alumno()
                + "/asistencias"), admin).getResponse().getStatus());
        assertEquals(200, conToken(get("/api/v1/admin/cursos/" + e.curso()
                + "/asistencia?bloqueId=" + e.bloque() + "&fecha=" + clase), admin)
                .getResponse().getStatus());

        // RBAC: cada rol en su zona
        assertEquals(403, conToken(get(DOCENTE + "/cursos/" + e.curso()
                + "/asistencia?bloqueId=" + e.bloque() + "&fecha=" + clase), token1)
                .getResponse().getStatus(), "un estudiante no registra asistencia");
        assertEquals(403, conToken(get("/api/v1/estudiante/asistencias"), tokenMama)
                .getResponse().getStatus(), "un apoderado no consulta como estudiante");
        assertEquals(403, conToken(get("/api/v1/secretaria/estudiantes/" + e.alumno()
                + "/asistencias"), token1).getResponse().getStatus(),
                "un estudiante no consulta la zona de secretaría");
    }

    @Test
    @DisplayName("Rechaza fecha futura, día incorrecto y bimestre cerrado")
    void validacionesDeFechaYEstado() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);

        LocalDate futura = clase;
        while (!futura.isAfter(LocalDate.now())) {
            futura = futura.plusWeeks(1);
        }
        assertEquals(400, registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), futura,
                "[{\"matriculaId\":%d,\"asistio\":true}]".formatted(e.matricula()))
                .getResponse().getStatus(), "no se registra una clase futura");

        assertEquals(400, registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(),
                clase.plusDays(1),
                "[{\"matriculaId\":%d,\"asistio\":true}]".formatted(e.matricula()))
                .getResponse().getStatus(), "la fecha no es el día del bloque");

        assertEquals(200, conToken(patch("/api/v1/admin/bimestres/" + e.bimestre() + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"CERRADO\"}"), admin).getResponse().getStatus());

        assertEquals(409, registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), clase,
                "[{\"matriculaId\":%d,\"asistio\":true}]".formatted(e.matricula()))
                .getResponse().getStatus(), "un bimestre cerrado no admite asistencia");

        // La lectura sigue disponible en un bimestre cerrado
        assertEquals(200, conToken(get(DOCENTE + "/cursos/" + e.curso()
                + "/asistencia?bloqueId=" + e.bloque() + "&fecha=" + clase), e.tokenDocente())
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("Rechaza matrícula de otra sección, curso ajeno y matrícula retirada")
    void validacionesDePertenencia() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);

        long seccionB = nuevaSeccion(3, "B", e.anio());
        long alumnoB = crearEstudiante("beto@educa360.pe", "30000002", "Beto");
        long matriculaB = matricular(alumnoB, seccionB, primerDia(e));
        assertEquals(404, registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), clase,
                "[{\"matriculaId\":%d,\"asistio\":true}]".formatted(matriculaB))
                .getResponse().getStatus(), "la matrícula es de otra sección");

        long docenteB = crearDocente("otto@educa360.pe", "20000002");
        long materiaB = crearMateria("Comunicación");
        assertEquals(200, habilitar(docenteB, "[" + materiaB + "]"));
        long cursoB = nuevoCurso(docenteB, materiaB, seccionB);
        assertEquals(404, conToken(get(DOCENTE + "/cursos/" + cursoB
                + "/asistencia?bloqueId=" + e.bloque() + "&fecha=" + clase), e.tokenDocente())
                .getResponse().getStatus(), "el curso ajeno no existe para este docente");

        assertEquals(200, conToken(patch("/api/v1/secretaria/matriculas/" + e.matricula()
                + "/retiro"), admin).getResponse().getStatus());
        assertEquals(409, registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), clase,
                "[{\"matriculaId\":%d,\"asistio\":true}]".formatted(e.matricula()))
                .getResponse().getStatus(), "una matrícula retirada no recibe asistencia");
    }

    @Test
    @DisplayName("Con asistencias no se reemplaza el horario ni se elimina el curso")
    void bloqueosPorAsistencia() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);

        assertEquals(200, registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), clase,
                "[{\"matriculaId\":%d,\"asistio\":true}]".formatted(e.matricula()))
                .getResponse().getStatus());

        MvcResult horario = conToken(put(CURSOS + "/" + e.curso() + "/horario")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bloques":[{"dia":"LUNES","horaInicio":"08:00","horaFin":"09:00"}]}
                        """), admin);
        assertEquals(409, horario.getResponse().getStatus(),
                "el horario no se reemplaza si ya hay asistencias");

        assertEquals(409, conToken(delete(CURSOS + "/" + e.curso()), admin)
                .getResponse().getStatus(), "el curso no se elimina si ya hay asistencias");
    }

    // ==================== Escenario ====================

    private record Escenario(long anio, long bimestre, long seccion, long docente,
                             long materia, long curso, long bloque, long alumno,
                             long matricula, String tokenDocente, LocalDate clase) {
    }

    private Escenario prepararEscenario(LocalDate clase) throws Exception {
        int anio = clase.getYear();
        String inicio = LocalDate.of(anio, 1, 1).toString();
        String fin = LocalDate.of(anio, 12, 31).toString();

        long anioId = crearAnio(anio, "Año Escolar " + anio, inicio);
        long bimestre = crearBimestre(anioId, 1, inicio, fin);
        long seccion = nuevaSeccion(3, "A", anioId);
        long docente = crearDocente("dana@educa360.pe", "20000001");
        long materia = crearMateria("Matemática");
        assertEquals(200, habilitar(docente, "[" + materia + "]"));
        long curso = nuevoCurso(docente, materia, seccion);
        long bloque = crearBloque(curso, diaDe(clase), "08:00", "09:00");

        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long matricula = matricular(alumno, seccion, LocalDate.of(anio, 1, 1));

        assertEquals(200, abrirAnio(anioId));
        assertEquals(200, abrirBimestre(bimestre));

        String tokenDocente = loginYToken("dana@educa360.pe", "Docente1234");
        return new Escenario(anioId, bimestre, seccion, docente, materia, curso, bloque,
                alumno, matricula, tokenDocente, clase);
    }

    private LocalDate clasePasada() {
        return LocalDate.now().minusWeeks(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate primerDia(Escenario e) {
        return LocalDate.of(e.clase().getYear(), 1, 1);
    }

    private String diaDe(LocalDate fecha) {
        return switch (fecha.getDayOfWeek()) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> throw new IllegalArgumentException("No hay clases el domingo");
        };
    }

    // ==================== Helpers de API ====================

    private MvcResult crear(String url, String body) throws Exception {
        return conToken(post(url).contentType(MediaType.APPLICATION_JSON).content(body), admin);
    }

    private MvcResult conToken(MockHttpServletRequestBuilder peticion, String token)
            throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult registrarAsistencia(String token, long cursoId, long bloqueId,
                                          LocalDate fecha, String asistenciasJson)
            throws Exception {
        return conToken(put(DOCENTE + "/cursos/" + cursoId + "/asistencia")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloqueId\":%d,\"fecha\":\"%s\",\"asistencias\":%s}"
                        .formatted(bloqueId, fecha, asistenciasJson)), token);
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

    private long crearBloque(long cursoId, String dia, String inicio, String fin)
            throws Exception {
        MvcResult resultado = conToken(put(CURSOS + "/" + cursoId + "/horario")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bloques":[{"dia":"%s","horaInicio":"%s","horaFin":"%s"}]}"""
                        .formatted(dia, inicio, fin)), admin);
        assertEquals(200, resultado.getResponse().getStatus(),
                resultado.getResponse().getContentAsString());
        return horarioRepository.findByCursoId(cursoId).get(0).getId();
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
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(email, password)))
                .andReturn();
        assertEquals(200, resultado.getResponse().getStatus());
        return tokenDe(resultado.getResponse().getContentAsString());
    }
}
