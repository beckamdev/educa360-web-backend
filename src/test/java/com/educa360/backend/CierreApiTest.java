package com.educa360.backend;

import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.AsistenciaRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Tests del Módulo 10: cierre anual, boletas, historial y reportes.
 *
 * Reglas que protege el suite:
 * - el cierre exige todos los bimestres CERRADOS;
 * - falta de notas de un curso bloquea el cierre;
 * - APROBADO exige todos los cursos ≥ 11.00 y asistencia global ≥ 70%;
 * - aprobar 5° grado deja al alumno EGRESADO;
 * - el cierre es idempotente;
 * - boletas e historial se calculan (no se guardan);
 * - reportes exigen año y validan bimestre/sección;
 * - RBAC por rol en cada endpoint.
 *
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class CierreApiTest {

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

    @Autowired
    private AsistenciaRepository asistenciaRepository;

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        // Orden de borrado por las FK: lo hijo primero.
        calificacionRepository.deleteAll();
        asistenciaRepository.deleteAll();
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
    @DisplayName("Flujo: cierre anual, boleta, historial, reportes e idempotencia")
    void flujoDeCierreAnual() throws Exception {
        Escenario e = prepararEscenario(3);

        completarBimestre(e, e.bimestreI(), "16.00", "15.00", LocalDate.of(2026, 3, 2));
        assertEquals(200, cerrarBimestre(e.bimestreI()));
        assertEquals(200, abrirBimestre(e.bimestreII()));
        completarBimestre(e, e.bimestreII(), "18.00", "17.00", LocalDate.of(2026, 5, 18));
        assertEquals(200, cerrarBimestre(e.bimestreII()));

        // Preview: 2 bimestres cerrados, un aprobado, promedio 16.50, asistencia 100%.
        MvcResult preview = conToken(get(ANIOS + "/" + e.anio() + "/cierre/preview"), admin);
        assertEquals(200, preview.getResponse().getStatus(),
                preview.getResponse().getContentAsString());
        String vista = preview.getResponse().getContentAsString();
        assertTrue(vista.contains("\"listo\":true"), vista);
        assertTrue(vista.contains("\"bimestres\":2"), vista);
        assertTrue(vista.contains("\"bimestresCerrados\":2"), vista);
        assertTrue(vista.contains("\"aprobados\":1"), vista);
        assertTrue(vista.contains("\"promedioGeneral\":16.50"), vista);
        assertTrue(vista.contains("\"asistenciaPorcentaje\":100.00"), vista);

        // Cierre: aplica la situación y deja el año CERRADO.
        MvcResult cierre = conToken(post(ANIOS + "/" + e.anio() + "/cerrar"), admin);
        assertEquals(200, cierre.getResponse().getStatus(),
                cierre.getResponse().getContentAsString());
        String cerrado = cierre.getResponse().getContentAsString();
        assertTrue(cerrado.contains("\"anioEstado\":\"CERRADO\""), cerrado);
        assertTrue(cerrado.contains("\"situacionActual\":\"APROBADO\""), cerrado);

        assertEquals(AnioEscolar.Estado.CERRADO,
                anioRepository.findById(e.anio()).orElseThrow().getEstado());
        Matricula matricula = matriculaRepository.findById(e.matricula()).orElseThrow();
        assertEquals(Matricula.SituacionFinal.APROBADO, matricula.getSituacionFinal());
        assertEquals(Estudiante.EstadoEstudiante.ACTIVO,
                estudianteRepository.findById(e.alumno()).orElseThrow().getEstado(),
                "3° grado no egresa");

        // Boleta del alumno (con año explícito: el año ya no está ABIERTO).
        String tokenAlma = loginYToken("alma@educa360.pe", "Alumno1234");
        MvcResult boleta = conToken(get("/api/v1/estudiante/boleta?anioId=" + e.anio()),
                tokenAlma);
        assertEquals(200, boleta.getResponse().getStatus(),
                boleta.getResponse().getContentAsString());
        String boletaJson = boleta.getResponse().getContentAsString();
        assertTrue(boletaJson.contains("\"situacionFinal\":\"APROBADO\""), boletaJson);
        assertTrue(boletaJson.contains("\"promedioGeneral\":16.50"), boletaJson);
        assertTrue(boletaJson.contains("\"asistenciaPorcentaje\":100.00"), boletaJson);
        assertTrue(boletaJson.contains("\"nombre\":\"Bimestre I\""), boletaJson);
        assertTrue(boletaJson.contains("\"nombre\":\"Bimestre II\""), boletaJson);

        // Boleta del apoderado.
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, e.alumno(), "MADRE", true));
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");
        MvcResult boletaHijo = conToken(get("/api/v1/apoderado/hijos/" + e.alumno()
                + "/boleta?anioId=" + e.anio()), tokenMama);
        assertEquals(200, boletaHijo.getResponse().getStatus(),
                boletaHijo.getResponse().getContentAsString());
        assertTrue(boletaHijo.getResponse().getContentAsString()
                .contains("\"situacionFinal\":\"APROBADO\""));

        // Historial para Secretaría (ADMIN también puede).
        crearSecretaria("sofia@educa360.pe", "21000001");
        String tokenSecretaria = loginYToken("sofia@educa360.pe", "Secreta1234");
        MvcResult historial = conToken(get("/api/v1/secretaria/estudiantes/"
                + e.alumno() + "/historial"), tokenSecretaria);
        assertEquals(200, historial.getResponse().getStatus(),
                historial.getResponse().getContentAsString());
        String historialJson = historial.getResponse().getContentAsString();
        assertTrue(historialJson.contains("\"anio\":2026"), historialJson);
        assertTrue(historialJson.contains("\"situacionFinal\":\"APROBADO\""), historialJson);
        assertTrue(historialJson.contains("\"promedioGeneral\":16.50"), historialJson);

        // Reportes anuales.
        String asistencia = conToken(get("/api/v1/admin/reportes/asistencia?anioId="
                + e.anio()), admin).getResponse().getContentAsString();
        assertTrue(asistencia.contains("\"total\":4"), asistencia);
        assertTrue(asistencia.contains("\"porcentaje\":100.00"), asistencia);

        String calificaciones = conToken(get("/api/v1/admin/reportes/calificaciones?anioId="
                + e.anio()), admin).getResponse().getContentAsString();
        assertTrue(calificaciones.contains("\"promedioGeneral\":16.50"), calificaciones);
        assertTrue(calificaciones.contains("\"materiaNombre\":\"Matemática\""), calificaciones);
        assertTrue(calificaciones.contains("\"materiaNombre\":\"Comunicación\""), calificaciones);

        // Idempotencia: repetir el cierre no rompe ni duplica efectos.
        MvcResult repetido = conToken(post(ANIOS + "/" + e.anio() + "/cerrar"), admin);
        assertEquals(200, repetido.getResponse().getStatus(),
                repetido.getResponse().getContentAsString());
        assertEquals(Matricula.SituacionFinal.APROBADO,
                matriculaRepository.findById(e.matricula()).orElseThrow().getSituacionFinal());

        // RBAC: el alumno no ve el cierre; el apoderado no ve un hijo ajeno;
        // Secretaría no entra a reportes de ADMIN.
        assertEquals(403, conToken(get(ANIOS + "/" + e.anio() + "/cierre/preview"),
                tokenAlma).getResponse().getStatus());
        long otro = crearEstudiante("beto@educa360.pe", "30000002", "Beto");
        assertEquals(404, conToken(get("/api/v1/apoderado/hijos/" + otro
                + "/boleta?anioId=" + e.anio()), tokenMama).getResponse().getStatus());
        assertEquals(403, conToken(get("/api/v1/admin/reportes/asistencia?anioId="
                + e.anio()), tokenSecretaria).getResponse().getStatus());

        // Validaciones de reportes: año obligatorio y bimestre de otro año.
        assertEquals(400, conToken(get("/api/v1/admin/reportes/asistencia"), admin)
                .getResponse().getStatus());
        long anio2027 = crearAnio(2027, "Año Escolar 2027", "2027-03-01");
        long bimestre2027 = crearBimestre(anio2027, 1, "2027-03-01", "2027-05-14");
        assertEquals(409, conToken(get("/api/v1/admin/reportes/calificaciones?anioId="
                + e.anio() + "&bimestreId=" + bimestre2027), admin)
                .getResponse().getStatus());
    }

    @Test
    @DisplayName("Aprobar 5° grado deja al alumno EGRESADO")
    void aprobarQuintoEgresa() throws Exception {
        Escenario e = prepararEscenario(5);

        completarBimestre(e, e.bimestreI(), "16.00", "16.00", LocalDate.of(2026, 3, 2));
        assertEquals(200, cerrarBimestre(e.bimestreI()));
        // El cierre anual exige TODOS los bimestres cerrados, aunque no tengan notas.
        assertEquals(200, cerrarBimestre(e.bimestreII()));

        assertEquals(200, conToken(post(ANIOS + "/" + e.anio() + "/cerrar"), admin)
                .getResponse().getStatus());

        Estudiante estudiante = estudianteRepository.findById(e.alumno()).orElseThrow();
        assertEquals(Estudiante.EstadoEstudiante.EGRESADO, estudiante.getEstado());
        assertEquals(Matricula.SituacionFinal.APROBADO,
                matriculaRepository.findById(e.matricula()).orElseThrow().getSituacionFinal());
    }

    @Test
    @DisplayName("Si falta el promedio de un curso, el cierre responde 409 y no cierra el año")
    void notasFaltantesBloqueanCierre() throws Exception {
        Escenario e = prepararEscenario(3);

        // Sólo se califica Matemática; Comunicación queda sin notas.
        calificarCurso(e, e.cursoMat(), e.bimestreI(), "Fracciones", "16.00");
        registrarPresente(e, e.cursoMat(), e.bloqueMat(), LocalDate.of(2026, 3, 2));
        assertEquals(200, cerrarBimestre(e.bimestreI()));
        assertEquals(200, cerrarBimestre(e.bimestreII()));

        String preview = conToken(get(ANIOS + "/" + e.anio() + "/cierre/preview"), admin)
                .getResponse().getContentAsString();
        assertTrue(preview.contains("\"listo\":false"), preview);
        assertTrue(preview.contains("\"conNotasFaltantes\":1"), preview);
        assertTrue(preview.contains("\"faltanNotas\":true"), preview);

        MvcResult cierre = conToken(post(ANIOS + "/" + e.anio() + "/cerrar"), admin);
        assertEquals(409, cierre.getResponse().getStatus(),
                cierre.getResponse().getContentAsString());

        assertEquals(AnioEscolar.Estado.ABIERTO,
                anioRepository.findById(e.anio()).orElseThrow().getEstado());
        assertEquals(Matricula.SituacionFinal.EN_CURSO,
                matriculaRepository.findById(e.matricula()).orElseThrow().getSituacionFinal());
    }

    // ==================== Escenario ====================

    private record Escenario(long anio, long bimestreI, long bimestreII, long seccion,
                             long cursoMat, long cursoCom, long bloqueMat, long bloqueCom,
                             long alumno, long matricula, String tokenDocente) {
    }

    private Escenario prepararEscenario(int grado) throws Exception {
        long anioId = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long bimestreI = crearBimestre(anioId, 1, "2026-03-02", "2026-05-15");
        long bimestreII = crearBimestre(anioId, 2, "2026-05-18", "2026-07-24");
        long seccion = nuevaSeccion(grado, "A", anioId);

        long docente = crearDocente("dana@educa360.pe", "20000001");
        long materiaMat = crearMateria("Matemática");
        long materiaCom = crearMateria("Comunicación");
        assertEquals(200, habilitar(docente, "[" + materiaMat + "," + materiaCom + "]"));
        long cursoMat = nuevoCurso(docente, materiaMat, seccion);
        long cursoCom = nuevoCurso(docente, materiaCom, seccion);
        long bloqueMat = crearBloque(cursoMat, "LUNES", "08:00", "09:00");
        long bloqueCom = crearBloque(cursoCom, "LUNES", "09:00", "10:00");

        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long matricula = matricular(alumno, seccion, LocalDate.of(2026, 3, 2));

        assertEquals(200, abrirAnio(anioId));
        assertEquals(200, abrirBimestre(bimestreI));

        String tokenDocente = loginYToken("dana@educa360.pe", "Docente1234");
        return new Escenario(anioId, bimestreI, bimestreII, seccion, cursoMat, cursoCom,
                bloqueMat, bloqueCom, alumno, matricula, tokenDocente);
    }

    /** Deja un bimestre completo: notas de ambos cursos y asistencia PRESENTE. */
    private void completarBimestre(Escenario e, long bimestreId, String notaMat,
                                   String notaCom, LocalDate fecha) throws Exception {
        calificarCurso(e, e.cursoMat(), bimestreId, "Unidad de Matemática", notaMat);
        calificarCurso(e, e.cursoCom(), bimestreId, "Unidad de Comunicación", notaCom);
        registrarPresente(e, e.cursoMat(), e.bloqueMat(), fecha);
        registrarPresente(e, e.cursoCom(), e.bloqueCom(), fecha);
    }

    private void calificarCurso(Escenario e, long cursoId, long bimestreId, String titulo,
                                String nota) throws Exception {
        long unidad = crearUnidad(e.tokenDocente(), cursoId, bimestreId, titulo);
        assertEquals(200, conToken(patch(DOCENTE + "/unidades/" + unidad + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"PUBLICADA\"}"), e.tokenDocente())
                .getResponse().getStatus());
        MvcResult registro = registrarNotas(e.tokenDocente(), unidad, """
                [{"matriculaId":%d,"criterio":"Examen","nota":%s}]"""
                .formatted(e.matricula(), nota));
        assertEquals(200, registro.getResponse().getStatus(),
                registro.getResponse().getContentAsString());
    }

    private void registrarPresente(Escenario e, long cursoId, long bloqueId, LocalDate fecha)
            throws Exception {
        MvcResult registro = registrarAsistencia(e.tokenDocente(), cursoId, bloqueId, fecha, """
                [{"matriculaId":%d,"asistio":true}]""".formatted(e.matricula()));
        assertEquals(200, registro.getResponse().getStatus(),
                registro.getResponse().getContentAsString());
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
        return cambiarEstadoBimestre(id, "ABIERTO");
    }

    private int cerrarBimestre(long id) throws Exception {
        return cambiarEstadoBimestre(id, "CERRADO");
    }

    private int cambiarEstadoBimestre(long id, String estado) throws Exception {
        return conToken(patch("/api/v1/admin/bimestres/" + id + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"" + estado + "\"}"), admin)
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

    private long crearSecretaria(String email, String dni) throws Exception {
        assertEquals(201, crear("/api/v1/admin/secretarias", """
                {"usuario":{"email":"%s","password":"Secreta1234","nombres":"Sofia",
                            "apellidos":"Quintos","dni":"%s","telefonos":["912345678"]},
                 "perfil":{}}""".formatted(email, dni)).getResponse().getStatus());
        return secretariaRepository.findAll().stream()
                .filter(s -> s.getUser().getEmail().equals(email))
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
