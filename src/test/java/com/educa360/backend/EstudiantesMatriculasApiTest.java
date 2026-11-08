package com.educa360.backend;

import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
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
 * Tests del Módulo 3: estudiantes, matrículas y representados.
 *
 * Reglas que protege el suite:
 * - la matrícula nace EN_CURSO y es única por (estudiante, año);
 * - sólo alumnos ACTIVOS se matriculan;
 * - el cambio de sección sólo se permite antes de iniciar clases;
 * - el retiro marca matrícula y alumno RETIRADOS; el reingreso los reactiva;
 * - el apoderado sólo ve a SUS hijos (baja lógica del vínculo);
 * - un SOLO responsable principal por estudiante;
 * - la sección que se muestra es la del año ABIERTO ("vigente");
 * - el ESTUDIANTE ve los cursos de SU sección; /auth/me trae el perfil.
 *
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class EstudiantesMatriculasApiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

    private static final String ESTUDIANTES = "/api/v1/secretaria/estudiantes";
    private static final String APODERADOS = "/api/v1/secretaria/apoderados";
    private static final String MATRICULAS = "/api/v1/secretaria/matriculas";
    private static final String FAMILIAS = "/api/v1/secretaria/familias";

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

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        // Orden de borrado por las FK: lo hijo primero
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

    // ==================== Flujo completo ====================

    @Test
    @DisplayName("Flujo: Secretaría matricula y vincula; el apoderado ve a su hijo con sección")
    void apoderadoVeAHijosConSeccion() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");

        assertEquals(201, vincular(mama, alumno, "MADRE", true));
        assertEquals(201, matricular(alumno, seccion));
        assertEquals(200, abrirAnio(anio), "el año se abre: es el vigente");

        String hijos = hijosDe("lucia@educa360.pe", "Familia1234");
        assertTrue(hijos.contains("\"nombres\":\"Alma\""), "debe ver a su hijo: " + hijos);
        assertTrue(hijos.contains("\"seccionEtiqueta\":\"3"), "con su sección: " + hijos);
        assertTrue(hijos.contains("\"anioNombre\":"), "con el año vigente: " + hijos);
        assertTrue(hijos.contains("\"situacionFinal\":\"EN_CURSO\""));
        assertTrue(hijos.contains("\"parentesco\":\"MADRE\""));
        assertTrue(hijos.contains("\"responsablePrincipal\":true"));
    }

    @Test
    @DisplayName("Hijo sin matrícula se ve con sección null; al abrir el año aparece")
    void seccionVigenteDependeDeAnioAbierto() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        vincular(mama, alumno, "MADRE", true);
        matricular(alumno, seccion); // año todavía PLANIFICADO: no hay vigente

        String sinAbrir = hijosDe("lucia@educa360.pe", "Familia1234");
        assertTrue(sinAbrir.contains("\"seccionEtiqueta\":null"),
                "sin año abierto no hay sección vigente: " + sinAbrir);

        abrirAnio(anio);
        String abierto = hijosDe("lucia@educa360.pe", "Familia1234");
        assertTrue(abierto.contains("\"seccionEtiqueta\":\"3"),
                "abierto el año, la sección aparece: " + abierto);
    }

    // ==================== Matrículas: reglas ====================

    @Test
    @DisplayName("La matrícula nace EN_CURSO y se consulta por id")
    void matriculaNaceEnCurso() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");

        MvcResult creada = crear(MATRICULAS, """
                {"estudianteId":%d,"seccionId":%d,"fechaMatricula":"2026-02-20"}"""
                .formatted(alumno, seccion));
        assertEquals(201, creada.getResponse().getStatus());
        assertTrue(creada.getResponse().getContentAsString().contains("\"situacionFinal\":\"EN_CURSO\""),
                "debe nacer en curso: " + creada.getResponse().getContentAsString());

        long matriculaId = idDe(creada);
        MvcResult detalle = conToken(get(MATRICULAS + "/" + matriculaId), admin);
        assertEquals(200, detalle.getResponse().getStatus());
        assertTrue(detalle.getResponse().getContentAsString().contains("\"fechaMatricula\":\"2026-02-20\""));
        assertEquals(404, conToken(get(MATRICULAS + "/999999"), admin).getResponse().getStatus(),
                "id inexistente");
    }

    @Test
    @DisplayName("Duplicada en la misma sección o dos en el mismo año responden 409")
    void matriculaDuplicadaResponde409() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccionA = nuevaSeccion(3, "A", anio);
        long seccionB = nuevaSeccion(3, "B", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");

        assertEquals(201, matricular(alumno, seccionA));
        assertEquals(409, matricular(alumno, seccionA), "misma sección = duplicada");
        assertEquals(409, matricular(alumno, seccionB),
                "otra sección del MISMO año = es un traslado, no una segunda inscripción");
    }

    @Test
    @DisplayName("Sólo alumnos ACTIVOS se matriculan (RETIRADO -> 409)")
    void matricularAlumnoRetiradoResponde409() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");

        Estudiante estudiante = estudianteRepository.findById(alumno).orElseThrow();
        estudiante.setEstado(Estudiante.EstadoEstudiante.RETIRADO);
        estudianteRepository.save(estudiante);

        assertEquals(409, matricular(alumno, seccion));
    }

    @Test
    @DisplayName("Matricular a un estudiante o sección inexistentes responde 404")
    void matriculaResponde404() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");

        assertEquals(404, matricular(999_999L, seccion));
        assertEquals(404, matricular(alumno, 999_999L));
    }

    @Test
    @DisplayName("Cambiar de sección antes del primer día de clases devuelve 200")
    void cambiarSeccionAntesDeIniciarClases() throws Exception {
        long anio = crearAnio(2099, "Año Escolar 2099", "2099-03-01");
        long seccionA = nuevaSeccion(3, "A", anio);
        long seccionB = nuevaSeccion(3, "B", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        assertEquals(201, matricular(alumno, seccionA));
        long matricula = matriculaRepository.findAll().get(0).getId();

        MvcResult cambio = conToken(patch(MATRICULAS + "/" + matricula + "/seccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seccionId\":%d}".formatted(seccionB)), admin);
        assertEquals(200, cambio.getResponse().getStatus());
        assertTrue(cambio.getResponse().getContentAsString().contains("\"seccionEtiqueta\":\"3°B\""),
                "la matrícula queda en la sección nueva: " + cambio.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Cambiar de sección con el año ya iniciado devuelve 409")
    void cambiarSeccionConAnioIniciadoResponde409() throws Exception {
        long anio = crearAnio(2020, "Año Escolar 2020", "2020-03-02");
        long seccionA = nuevaSeccion(3, "A", anio);
        long seccionB = nuevaSeccion(3, "B", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        assertEquals(201, matricular(alumno, seccionA));
        long matricula = matriculaRepository.findAll().get(0).getId();

        assertEquals(409, conToken(patch(MATRICULAS + "/" + matricula + "/seccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seccionId\":%d}".formatted(seccionB)), admin)
                .getResponse().getStatus(), "el año ya inició clases");
    }

    @Test
    @DisplayName("Cambiar a una sección de otro año devuelve 409")
    void cambiarSeccionDeOtroAnioResponde409() throws Exception {
        long anio = crearAnio(2099, "Año Escolar 2099", "2099-03-01");
        long otroAnio = crearAnio(2098, "Año Escolar 2098", "2098-03-01");
        long seccionA = nuevaSeccion(3, "A", anio);
        long seccionOtroAnio = nuevaSeccion(3, "A", otroAnio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        assertEquals(201, matricular(alumno, seccionA));
        long matricula = matriculaRepository.findAll().get(0).getId();

        assertEquals(409, conToken(patch(MATRICULAS + "/" + matricula + "/seccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seccionId\":%d}".formatted(seccionOtroAnio)), admin)
                .getResponse().getStatus(), "la sección nueva es de otro año");
    }

    @Test
    @DisplayName("Retiro marca matrícula y alumno RETIRADOS; el reingreso los reactiva")
    void retiroYReingreso() throws Exception {
        long anio = crearAnio(2027, "Año Escolar 2027", "2027-03-01");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        assertEquals(201, matricular(alumno, seccion));
        long matricula = matriculaRepository.findAll().get(0).getId();

        MvcResult retiro = conToken(patch(MATRICULAS + "/" + matricula + "/retiro"), admin);
        assertEquals(200, retiro.getResponse().getStatus());
        assertTrue(retiro.getResponse().getContentAsString().contains("\"situacionFinal\":\"RETIRADO\""));
        assertEquals(Estudiante.EstadoEstudiante.RETIRADO,
                estudianteRepository.findById(alumno).orElseThrow().getEstado());
        assertEquals(409, matricular(alumno, seccion), "un alumno retirado no se vuelve a matricular");

        assertEquals(200, abrirAnio(anio), "el año sigue abierto para el reingreso");
        MvcResult reingreso = conToken(patch(ESTUDIANTES + "/" + alumno + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"), admin);
        assertEquals(200, reingreso.getResponse().getStatus());
        assertEquals(Estudiante.EstadoEstudiante.ACTIVO,
                estudianteRepository.findById(alumno).orElseThrow().getEstado());
        assertEquals(Matricula.SituacionFinal.EN_CURSO,
                matriculaRepository.findById(matricula).orElseThrow().getSituacionFinal(),
                "el reingreso reactiva la matrícula vigente");
    }

    @Test
    @DisplayName("El listado de matrículas filtra por año, sección y texto")
    void listarMatriculas() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        matricular(alumno, seccion);

        String porAnio = listar(MATRICULAS + "?anioId=" + anio);
        assertTrue(porAnio.contains("\"situacionFinal\":\"EN_CURSO\""), porAnio);
        assertTrue(porAnio.contains("Alma"), porAnio);
        assertTrue(listar(MATRICULAS + "?seccionId=" + seccion).contains("Alma"));
        assertTrue(listar(MATRICULAS + "?q=alma").contains("Alma"));
        assertFalse(listar(MATRICULAS + "?q=zzz").contains("Alma"));
    }

    @Test
    @DisplayName("GET /estudiante/matricula devuelve la matrícula vigente del alumno")
    void estudianteConsultaSuMatricula() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        String token = loginYToken("alma@educa360.pe", "Alumno1234");

        assertEquals(404, conToken(get("/api/v1/estudiante/matricula"), token)
                .getResponse().getStatus(), "sin matrícula todavía");

        matricular(alumno, seccion);
        abrirAnio(anio);

        MvcResult suya = conToken(get("/api/v1/estudiante/matricula"), token);
        assertEquals(200, suya.getResponse().getStatus());
        assertTrue(suya.getResponse().getContentAsString().contains("\"seccionEtiqueta\":\"3"),
                suya.getResponse().getContentAsString());
        assertTrue(suya.getResponse().getContentAsString().contains("\"situacionFinal\":\"EN_CURSO\""));
    }

    @Test
    @DisplayName("PUT /secretaria/estudiantes/{id} edita fecha de nacimiento y estado")
    void editarFichaAcotada() throws Exception {
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");

        MvcResult actualizado = conToken(put(ESTUDIANTES + "/" + alumno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaNacimiento\":\"2010-05-05\",\"estado\":\"TRASLADADO\"}"), admin);
        assertEquals(200, actualizado.getResponse().getStatus());
        String cuerpo = actualizado.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"fechaNacimiento\":\"2010-05-05\""), cuerpo);
        assertTrue(cuerpo.contains("\"estado\":\"TRASLADADO\""), cuerpo);
    }

    // ==================== Representados ====================

    @Test
    @DisplayName("Baja lógica del vínculo: deja de verse y re-vincular lo reactiva")
    void desvincularYReactivar() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        vincular(mama, alumno, "MADRE", true);
        abrirAnio(anio);

        assertEquals(204, desvincular(mama, alumno));
        assertEquals(404, desvincular(mama, alumno), "ya estaba desvinculado");
        String vacio = hijosDe("lucia@educa360.pe", "Familia1234");
        assertTrue(vacio.contains("[]"), "ya no ve hijos: " + vacio);

        assertEquals(201, vincular(mama, alumno, "TUTOR", false), "re-vincular reactiva");
        String otraVez = hijosDe("lucia@educa360.pe", "Familia1234");
        assertTrue(otraVez.contains("\"parentesco\":\"TUTOR\""), "con el parentesco nuevo: " + otraVez);
        assertFalse(otraVez.contains("\"responsablePrincipal\":true"));
    }

    @Test
    @DisplayName("Sólo UN responsable principal por estudiante")
    void unSoloResponsablePrincipal() throws Exception {
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long papa = crearApoderado("pedro@educa360.pe", "40000002", "Pedro");
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");

        assertEquals(201, vincular(papa, alumno, "PADRE", true));
        assertEquals(201, vincular(mama, alumno, "MADRE", true), "la nueva principal desplaza a la anterior");

        String ficha = listar(ESTUDIANTES);
        assertEquals(1, ocurrencias(ficha, "\"responsablePrincipal\":true"),
                "exactamente un responsable principal: " + ficha);
        assertTrue(ficha.contains("\"nombres\":\"Lucia Soto\""),
                "la nueva principal debe aparecer en la ficha: " + ficha);
    }

    @Test
    @DisplayName("Cada apoderado sólo ve a SUS hijos")
    void apoderadoNoVeHijosAjenos() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long hijoA = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long hijoB = crearEstudiante("bruno@educa360.pe", "30000002", "Bruno");
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        long otro = crearApoderado("pedro@educa360.pe", "40000002", "Pedro");
        vincular(mama, hijoA, "MADRE", true);
        vincular(otro, hijoB, "PADRE", true);
        matricular(hijoA, seccion);
        abrirAnio(anio);

        String deMama = hijosDe("lucia@educa360.pe", "Familia1234");
        assertTrue(deMama.contains("Alma"));
        assertFalse(deMama.contains("Bruno"), "no ve el hijo del otro apoderado");

        String dePedro = hijosDe("pedro@educa360.pe", "Familia1234");
        assertTrue(dePedro.contains("Bruno"));
        assertFalse(dePedro.contains("Alma"));
    }

    // ==================== Listados de Secretaría ====================

    @Test
    @DisplayName("GET /secretaria/estudiantes trae la ficha con sección, matrícula y apoderados")
    void listarEstudiantesConFichaCompleta() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(4, "B", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        matricular(alumno, seccion);
        vincular(mama, alumno, "MADRE", true);
        abrirAnio(anio);

        String cuerpo = listar(ESTUDIANTES);
        assertTrue(cuerpo.contains("\"email\":\"alma@educa360.pe\""));
        assertTrue(cuerpo.contains("\"estado\":\"ACTIVO\""), "estado académico");
        assertTrue(cuerpo.contains("\"seccionEtiqueta\":\"4"), "sección vigente");
        assertTrue(cuerpo.contains("\"situacionFinal\":\"EN_CURSO\""));
        assertTrue(cuerpo.contains("\"fechaMatricula\""), "la ficha lleva la fecha de matrícula");
        assertTrue(cuerpo.contains("\"parentesco\":\"MADRE\""), "sus apoderados");
        assertTrue(cuerpo.contains("\"fechaNacimiento\""), "EstudianteResponse lleva la ficha");

        // Filtros: ?q= y ?estado=
        assertTrue(listar(ESTUDIANTES + "?q=alma").contains("alma@educa360.pe"));
        assertFalse(listar(ESTUDIANTES + "?q=no-existe").contains("alma@educa360.pe"));
        assertFalse(listar(ESTUDIANTES + "?estado=EGRESADO").contains("alma@educa360.pe"));
        assertTrue(listar(ESTUDIANTES + "?estado=ACTIVO").contains("alma@educa360.pe"));

        assertEquals(200, conToken(get(ESTUDIANTES + "/" + alumno), admin)
                .getResponse().getStatus(), "la ficha individual responde 200");
        assertEquals(404, conToken(get(ESTUDIANTES + "/999999"), admin)
                .getResponse().getStatus(), "alumno inexistente");
    }

    @Test
    @DisplayName("GET /secretaria/apoderados trae a las familias con sus hijos")
    void listarApoderadosConHijos() throws Exception {
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        vincular(mama, alumno, "MADRE", false);

        String cuerpo = listar(APODERADOS);
        assertTrue(cuerpo.contains("\"email\":\"lucia@educa360.pe\""));
        assertTrue(cuerpo.contains("\"hijos\""), "con sus representados");
        assertTrue(cuerpo.contains("\"parentesco\":\"MADRE\""));
        assertTrue(listar(APODERADOS + "?q=lucia").contains("lucia@educa360.pe"));
        assertFalse(listar(APODERADOS + "?q=zzz").contains("lucia@educa360.pe"));
    }

    // ==================== Zona ESTUDIANTE ====================

    @Test
    @DisplayName("El ESTUDIANTE ve los cursos de SU sección (y lista vacía sin matrícula)")
    void estudianteVeLosCursosDeSuSeccion() throws Exception {
        long docente = crearDocente("doc1@educa360.pe", "20000011");
        long materia = crearMateria("Matematicas");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        habilitar(docente, "[%d]".formatted(materia));
        nuevoCurso(docente, materia, seccion);
        abrirAnio(anio);

        String tokenSinMatricula;
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        tokenSinMatricula = loginYToken("alma@educa360.pe", "Alumno1234");

        String vacio = conToken(get("/api/v1/estudiante/cursos"), tokenSinMatricula)
                .getResponse().getContentAsString();
        assertTrue(vacio.contains("[]"), "sin matrícula no hay cursos: " + vacio);

        matricular(alumno, seccion);
        String cursos = conToken(get("/api/v1/estudiante/cursos"), tokenSinMatricula)
                .getResponse().getContentAsString();
        assertTrue(cursos.contains("Matematicas"), "con matrícula ve los de su sección: " + cursos);
        assertTrue(cursos.contains("\"seccionEtiqueta\":\"3"));
    }

    // ==================== Alta de familias ====================

    @Test
    @DisplayName("Buscar apoderado por DNI: existe=true con id de perfil, o existe=false")
    void buscarApoderadoPorDni() throws Exception {
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");

        MvcResult encontrado = conToken(get(APODERADOS + "/por-dni/40000001"), admin);
        assertEquals(200, encontrado.getResponse().getStatus());
        String cuerpo = encontrado.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"existe\":true"), cuerpo);
        assertEquals(mama, campoLong(cuerpo, "id"), "el id debe ser el del PERFIL: " + cuerpo);
        assertTrue(cuerpo.contains("\"dni\":\"40000001\""), cuerpo);

        MvcResult libre = conToken(get(APODERADOS + "/por-dni/49999999"), admin);
        assertEquals(200, libre.getResponse().getStatus(), "no existir no es un error");
        String sin = libre.getResponse().getContentAsString();
        assertTrue(sin.contains("\"existe\":false"), sin);
        assertTrue(sin.contains("\"apoderado\":null"), sin);

        assertEquals(400, conToken(get(APODERADOS + "/por-dni/123"), admin)
                .getResponse().getStatus(), "DNI mal formado");
    }

    @Test
    @DisplayName("Alta de familia: crea apoderado y estudiante y los vincula")
    void altaFamiliaCreaYVincula() throws Exception {
        MvcResult creada = crear(FAMILIAS, """
                {"nuevoApoderado":{"email":"lucia@educa360.pe","password":"Familia1234",
                  "nombres":"Lucia","apellidos":"Soto","dni":"40000001","telefonos":["999888777"]},
                 "estudiante":{"usuario":{"email":"alma@educa360.pe","password":"Alumno1234",
                  "nombres":"Alma","apellidos":"Luna","dni":"30000001"},
                  "fechaNacimiento":"2011-03-03"},
                 "parentesco":"MADRE","responsablePrincipal":true}
                """);
        assertEquals(201, creada.getResponse().getStatus(),
                creada.getResponse().getContentAsString());

        String cuerpo = creada.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"apoderadoCreado\":true"), cuerpo);
        long apoderadoId = campoLong(cuerpo, "apoderadoId");
        long estudianteId = campoLong(cuerpo, "estudianteId");

        assertEquals(1, apoderadoRepository.count());
        assertEquals(1, estudianteRepository.count());
        assertEquals(1, vinculoRepository.findByEstudianteIdAndActivoTrue(estudianteId).size());
        assertTrue(vinculoRepository.findByEstudianteIdAndActivoTrue(estudianteId)
                .get(0).isResponsablePrincipal());

        String ficha = conToken(get(ESTUDIANTES + "/" + estudianteId), admin)
                .getResponse().getContentAsString();
        assertTrue(ficha.contains("\"id\":" + apoderadoId), ficha);
        assertTrue(ficha.contains("Lucia Soto"), ficha);
    }

    @Test
    @DisplayName("Alta de familia reutiliza el apoderado si el DNI ya existe")
    void altaFamiliaReutilizaApoderadoPorDni() throws Exception {
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");

        MvcResult creada = crear(FAMILIAS, """
                {"nuevoApoderado":{"email":"otro@educa360.pe","password":"Familia1234",
                  "nombres":"Otro","apellidos":"Nombre","dni":"40000001"},
                 "estudiante":{"usuario":{"email":"alma@educa360.pe","password":"Alumno1234",
                  "nombres":"Alma","apellidos":"Luna","dni":"30000001"},
                  "fechaNacimiento":"2011-03-03"},
                 "parentesco":"MADRE","responsablePrincipal":true}
                """);
        assertEquals(201, creada.getResponse().getStatus(),
                creada.getResponse().getContentAsString());

        String cuerpo = creada.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"apoderadoCreado\":false"), cuerpo);
        assertEquals(mama, campoLong(cuerpo, "apoderadoId"));
        assertEquals(1, apoderadoRepository.count(), "no debe duplicar el apoderado");
    }

    @Test
    @DisplayName("Alta de familia con apoderadoId existente no crea otro apoderado")
    void altaFamiliaConApoderadoId() throws Exception {
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");

        MvcResult creada = crear(FAMILIAS, """
                {"apoderadoId":%d,
                 "estudiante":{"usuario":{"email":"alma@educa360.pe","password":"Alumno1234",
                  "nombres":"Alma","apellidos":"Luna","dni":"30000001"},
                  "fechaNacimiento":"2011-03-03"},
                 "parentesco":"MADRE","responsablePrincipal":true}
                """.formatted(mama));
        assertEquals(201, creada.getResponse().getStatus(),
                creada.getResponse().getContentAsString());

        String cuerpo = creada.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"apoderadoCreado\":false"), cuerpo);
        assertEquals(mama, campoLong(cuerpo, "apoderadoId"));
        assertEquals(1, apoderadoRepository.count());
    }

    @Test
    @DisplayName("Alta de familia exige exactamente un apoderado y prohíbe teléfonos del alumno")
    void altaFamiliaValidaDatos() throws Exception {
        assertEquals(400, crear(FAMILIAS, """
                {"estudiante":{"usuario":{"email":"alma@educa360.pe","password":"Alumno1234",
                  "nombres":"Alma","apellidos":"Luna","dni":"30000001"},
                  "fechaNacimiento":"2011-03-03"},
                 "parentesco":"MADRE","responsablePrincipal":true}
                """).getResponse().getStatus(), "sin apoderado");

        assertEquals(400, crear(FAMILIAS, """
                {"apoderadoId":1,
                 "nuevoApoderado":{"email":"lucia@educa360.pe","password":"Familia1234",
                  "nombres":"Lucia","apellidos":"Soto","dni":"40000001"},
                 "estudiante":{"usuario":{"email":"alma@educa360.pe","password":"Alumno1234",
                  "nombres":"Alma","apellidos":"Luna","dni":"30000001"},
                  "fechaNacimiento":"2011-03-03"},
                 "parentesco":"MADRE","responsablePrincipal":true}
                """).getResponse().getStatus(), "con ambos apoderados");

        assertEquals(400, crear(FAMILIAS, """
                {"nuevoApoderado":{"email":"lucia@educa360.pe","password":"Familia1234",
                  "nombres":"Lucia","apellidos":"Soto","dni":"40000001"},
                 "estudiante":{"usuario":{"email":"alma@educa360.pe","password":"Alumno1234",
                  "nombres":"Alma","apellidos":"Luna","dni":"30000001","telefonos":["999888777"]},
                  "fechaNacimiento":"2011-03-03"},
                 "parentesco":"MADRE","responsablePrincipal":true}
                """).getResponse().getStatus(), "el alumno no registra teléfonos");
    }

    @Test
    @DisplayName("Alta de familia con un DNI que ya es de otro rol responde 409")
    void altaFamiliaDniDeOtroRol() throws Exception {
        crearEstudiante("alma@educa360.pe", "30000001", "Alma");

        assertEquals(409, crear(FAMILIAS, """
                {"nuevoApoderado":{"email":"lucia@educa360.pe","password":"Familia1234",
                  "nombres":"Lucia","apellidos":"Soto","dni":"30000001"},
                 "estudiante":{"usuario":{"email":"nuevo@educa360.pe","password":"Alumno1234",
                  "nombres":"Nuevo","apellidos":"Alumno","dni":"30000002"},
                  "fechaNacimiento":"2011-03-03"},
                 "parentesco":"MADRE","responsablePrincipal":true}
                """).getResponse().getStatus());
    }

    // ==================== /auth/me por rol ====================

    @Test
    @DisplayName("/auth/me trae la sección del ESTUDIANTE y los hijos del APODERADO")
    void meTraeElPerfilDeEstudianteYApoderado() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02");
        long seccion = nuevaSeccion(3, "A", anio);
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        vincular(mama, alumno, "MADRE", true);
        matricular(alumno, seccion);
        abrirAnio(anio);

        String meAlumno = conToken(get("/api/v1/auth/me"),
                        loginYToken("alma@educa360.pe", "Alumno1234"))
                .getResponse().getContentAsString();
        assertTrue(meAlumno.contains("\"seccionEtiqueta\":\"3"),
                "el estudiante ve su sección: " + meAlumno);
        assertTrue(meAlumno.contains("\"anioNombre\":"), "el año vigente del alumno: " + meAlumno);

        String meMama = conToken(get("/api/v1/auth/me"),
                        loginYToken("lucia@educa360.pe", "Familia1234"))
                .getResponse().getContentAsString();
        assertTrue(meMama.contains("\"hijos\""), "el apoderado ve sus hijos: " + meMama);
        assertTrue(meMama.contains("Alma"));
    }

    // ==================== RBAC ====================

    @Test
    @DisplayName("Las zonas nuevas sólo admiten a su rol")
    void rbacDeLasZonasNuevas() throws Exception {
        long alumno = crearEstudiante("alma@educa360.pe", "30000001", "Alma");
        crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        String tokenAlumno = loginYToken("alma@educa360.pe", "Alumno1234");
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");

        // Sin token -> 401 en todos
        assertEquals(401, mockMvc.perform(get("/api/v1/apoderado/hijos")).andReturn()
                .getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get(ESTUDIANTES)).andReturn()
                .getResponse().getStatus());

        // El rol equivocado -> 403
        assertEquals(403, conToken(get("/api/v1/apoderado/hijos"), tokenAlumno)
                .getResponse().getStatus(), "un estudiante no consulta hijos");
        assertEquals(403, conToken(get("/api/v1/estudiante/cursos"), tokenMama)
                .getResponse().getStatus(), "un apoderado no consulta cursos de estudiante");
        assertEquals(403, conToken(get(ESTUDIANTES), tokenAlumno)
                .getResponse().getStatus(), "un estudiante no consulta el censo");
        assertEquals(403, conToken(post(MATRICULAS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estudianteId\":%d,\"seccionId\":1,\"fechaMatricula\":\"2026-02-20\"}"
                                .formatted(alumno)), tokenMama)
                .getResponse().getStatus(), "un apoderado no matricula");

        // ADMIN sí gestiona (zona secretaria con ADMIN como superconjunto)
        assertEquals(200, conToken(get(ESTUDIANTES), admin).getResponse().getStatus());
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
        return idDe(crear("/api/v1/admin/anios", """
                {"anio":%d,"nombre":"%s","fechaInicio":"%s"}"""
                .formatted(anio, nombre, inicio)));
    }

    private int abrirAnio(long id) throws Exception {
        return conToken(patch("/api/v1/admin/anios/" + id + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ABIERTO\"}"), admin)
                .getResponse().getStatus();
    }

    private long nuevaSeccion(int grado, String letra, long anioId) throws Exception {
        return idDe(crear("/api/v1/admin/secciones",
                "{\"grado\":%d,\"letra\":\"%s\",\"aula\":\"A-101\",\"tutorId\":null,\"anioId\":%d}"
                        .formatted(grado, letra, anioId)));
    }

    /** Id del PERFIL Estudiante (la respuesta de alta trae el id de la cuenta). */
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

    /** Id del PERFIL Apoderado. */
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

    private int desvincular(long apoderadoId, long estudianteId) throws Exception {
        return conToken(delete(APODERADOS + "/" + apoderadoId + "/representados/" + estudianteId),
                admin).getResponse().getStatus();
    }

    private String listar(String url) throws Exception {
        return conToken(get(url), admin).getResponse().getContentAsString();
    }

    private String hijosDe(String email, String password) throws Exception {
        return conToken(get("/api/v1/apoderado/hijos"), loginYToken(email, password))
                .getResponse().getContentAsString();
    }

    private long crearDocente(String email, String dni) throws Exception {
        assertEquals(201, crear("/api/v1/admin/docentes", """
                {"usuario":{"email":"%s","password":"Docente1234","nombres":"Dana","apellidos":"Perez",
                            "dni":"%s"},
                 "perfil":{"titulo":"Lic. en Matematicas"}}
                """.formatted(email, dni)).getResponse().getStatus());
        return docenteRepository.findAll().stream()
                .filter(d -> d.getUser().getEmail().equals(email))
                .findFirst().orElseThrow().getId();
    }

    private long crearMateria(String nombre) throws Exception {
        return idDe(crear("/api/v1/admin/materias", "{\"nombre\":\"%s\"}".formatted(nombre)));
    }

    private int habilitar(long docenteId, String materiaIdsJson) throws Exception {
        return conToken(put("/api/v1/admin/docentes/" + docenteId + "/materias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materiaIds\":" + materiaIdsJson + "}"), admin)
                .getResponse().getStatus();
    }

    private long nuevoCurso(long docenteId, long materiaId, long seccionId) throws Exception {
        return idDe(crear("/api/v1/admin/cursos", """
                {"docenteId":%d,"materiaId":%d,"seccionId":%d}"""
                .formatted(docenteId, materiaId, seccionId)));
    }

    private long idDe(MvcResult resultado) throws Exception {
        assertEquals(201, resultado.getResponse().getStatus(),
                "creación inesperada: " + resultado.getResponse().getContentAsString());
        Matcher matcher = Pattern.compile("\"id\":(\\d+)")
                .matcher(resultado.getResponse().getContentAsString());
        assertTrue(matcher.find(), "la respuesta debe traer el id");
        return Long.parseLong(matcher.group(1));
    }

    /** Lee un campo numérico de un JSON por nombre (p. ej. apoderadoId). */
    private long campoLong(String json, String campo) {
        Matcher matcher = Pattern.compile("\"" + campo + "\":(\\d+)").matcher(json);
        assertTrue(matcher.find(), "no se encontró " + campo + " en " + json);
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

    private int ocurrencias(String texto, String fragmento) {
        int total = 0;
        int desde = 0;
        while ((desde = texto.indexOf(fragmento, desde)) != -1) {
            total++;
            desde += fragmento.length();
        }
        return total;
    }
}
