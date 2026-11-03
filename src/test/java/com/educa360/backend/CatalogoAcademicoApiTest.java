package com.educa360.backend;

import com.educa360.backend.repository.AnioEscolarRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.BimestreRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.HorarioBloqueRepository;
import com.educa360.backend.repository.MateriaRepository;
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
 * Tests del Módulo 2: estructura académica anual (años, bimestres, materias,
 * secciones, habilitaciones, cursos y horarios).
 *
 * Reglas que protege el suite:
 * - sólo UN año ABIERTO y sólo UN bimestre ABIERTO a la vez;
 * - los años no se solapan y los bimestres viven dentro de su año;
 * - un año CERRADO es solo lectura;
 * - "grado+letra" único por año (secundaria: grado 1..5);
 * - único curso por (materia, sección);
 * - sin habilitación previa del docente no se crea el curso;
 * - materia desactivada frena nuevos cursos;
 * - el horario no solapa a la sección ni al docente;
 * - el docente sólo consulta el horario de SUS cursos.
 *
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoAcademicoApiTest {

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
    private static final String MATERIAS = "/api/v1/admin/materias";
    private static final String SECCIONES = "/api/v1/admin/secciones";
    private static final String CURSOS = "/api/v1/admin/cursos";

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

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        // Orden de borrado por las FK: lo hijo primero
        horarioRepository.deleteAll();
        cursoRepository.deleteAll();
        seccionRepository.deleteAll();
        docenteRepository.deleteAll(); // con ella se van las filas de la join docente_materia
        materiaRepository.deleteAll();
        bimestreRepository.deleteAll();
        anioRepository.deleteAll();
        estudianteRepository.deleteAll(); // el test de RBAC crea uno
        secretariaRepository.deleteAll();
        apoderadoRepository.deleteAll();
        userRepository.deleteAll();

        // Alta inicial: el primer usuario de la BD siempre es ADMIN
        admin = registrarAdmin();
    }

    // ==================== Años escolares ====================

    @Test
    @DisplayName("El año nace PLANIFICADO y sólo se abre con PATCH /estado")
    void anioNacePlanificadoYSeAbre() throws Exception {
        MvcResult creado = crear(ANIOS, anioBody(2026, "Año Escolar 2026",
                "2026-03-02", "2026-12-18"));
        assertEquals(201, creado.getResponse().getStatus());
        String cuerpo = creado.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"estado\":\"PLANIFICADO\""), "debe nacer planificado: " + cuerpo);

        long id = idDe(creado);
        MvcResult abierto = conToken(patch(ANIOS + "/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ABIERTO\"}"), admin);
        assertEquals(200, abierto.getResponse().getStatus());
        assertTrue(abierto.getResponse().getContentAsString().contains("\"estado\":\"ABIERTO\""));
    }

    @Test
    @DisplayName("Fechas invertidas (fin antes que inicio) se rechazan con 400")
    void fechasInvertidasResponde400() throws Exception {
        MvcResult resultado = crear(ANIOS, anioBody(2026, "Raro", "2026-12-18", "2026-03-02"));

        assertEquals(400, resultado.getResponse().getStatus());
    }

    @Test
    @DisplayName("Sólo UN año abierto a la vez: abrir otro con vigente responde 409")
    void soloUnAnioAbiertoALaVez() throws Exception {
        long primero = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long segundo = crearAnio(2027, "Año Escolar 2027", "2027-03-01", "2027-12-17");

        assertEquals(200, cambiarEstadoAnio(primero, "ABIERTO"));
        assertEquals(409, cambiarEstadoAnio(segundo, "ABIERTO"), "no puede haber dos vigentes");

        assertEquals(200, cambiarEstadoAnio(primero, "CERRADO"), "cerrar siempre es válido");
        assertEquals(200, cambiarEstadoAnio(segundo, "ABIERTO"), "al cerrar el uno, el otro ya puede abrir");
    }

    @Test
    @DisplayName("Año o nombre duplicados se rechazan con 409")
    void anioYNombreDuplicadosResponden409() throws Exception {
        crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");

        assertEquals(409, crear(ANIOS, anioBody(2026, "Otro nombre",
                "2027-03-01", "2027-12-17")).getResponse().getStatus(),
                "el número de año es único");
        assertEquals(409, crear(ANIOS, anioBody(2027, "Año Escolar 2026",
                "2027-03-01", "2027-12-17")).getResponse().getStatus(),
                "el nombre es único");
    }

    @Test
    @DisplayName("Dos años con fechas solapadas se rechazan con 409")
    void aniosSolapadosResponden409() throws Exception {
        crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");

        assertEquals(409, crear(ANIOS, anioBody(2027, "Año Escolar 2027",
                "2026-12-01", "2027-11-30")).getResponse().getStatus(),
                "las fechas se solapan con el año 2026");
    }

    @Test
    @DisplayName("Un año CERRADO es solo lectura: no se edita ni se crea estructura")
    void anioCerradoEsSoloLectura() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion = nuevaSeccion(3, "A", anio);
        long docente = crearDocente("doc1@educa360.pe", "20000011");
        long materia = crearMateria("Matematicas");
        habilitar(docente, "[%d]".formatted(materia));
        assertEquals(200, cambiarEstadoAnio(anio, "CERRADO"));

        assertEquals(409, conToken(put(ANIOS + "/" + anio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(anioBody(2026, "Año Escolar 2026",
                        "2026-03-02", "2026-12-18")), admin)
                .getResponse().getStatus(), "editar un año cerrado");
        assertEquals(409, estadoDeSeccion(3, "B", anio, null), "crear secciones en un año cerrado");
        assertEquals(409, crear(BIMESTRES_DE(anio), """
                {"numero":1,"fechaInicio":"2026-03-02","fechaFin":"2026-05-15"}""")
                .getResponse().getStatus(), "crear bimestres en un año cerrado");
        assertEquals(409, curso(docente, materia, seccion), "crear cursos en un año cerrado");
    }

    @Test
    @DisplayName("No se cierra un año con bimestres ABIERTOS (409)")
    void noSeCierraAnioConBimestreAbierto() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        assertEquals(200, cambiarEstadoAnio(anio, "ABIERTO"));
        long bimestre = crearBimestre(anio, 1, "2026-03-02", "2026-05-15");
        assertEquals(200, cambiarEstadoBimestre(bimestre, "ABIERTO"));

        assertEquals(409, cambiarEstadoAnio(anio, "CERRADO"),
                "primero hay que cerrar el bimestre");

        assertEquals(200, cambiarEstadoBimestre(bimestre, "CERRADO"));
        assertEquals(200, cambiarEstadoAnio(anio, "CERRADO"));
    }

    // ==================== Bimestres ====================

    @Test
    @DisplayName("El bimestre sólo se abre si el año está ABIERTO (409)")
    void bimestreSoloSeAbreConAnioAbierto() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long bimestre = crearBimestre(anio, 1, "2026-03-02", "2026-05-15");

        assertEquals(409, cambiarEstadoBimestre(bimestre, "ABIERTO"),
                "el año aún está PLANIFICADO");

        assertEquals(200, cambiarEstadoAnio(anio, "ABIERTO"));
        assertEquals(200, cambiarEstadoBimestre(bimestre, "ABIERTO"));
    }

    @Test
    @DisplayName("Sólo UN bimestre abierto a la vez (409)")
    void soloUnBimestreAbiertoALaVez() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        assertEquals(200, cambiarEstadoAnio(anio, "ABIERTO"));
        long primero = crearBimestre(anio, 1, "2026-03-02", "2026-05-15");
        long segundo = crearBimestre(anio, 2, "2026-05-18", "2026-07-31");

        assertEquals(200, cambiarEstadoBimestre(primero, "ABIERTO"));
        assertEquals(409, cambiarEstadoBimestre(segundo, "ABIERTO"));

        assertEquals(200, cambiarEstadoBimestre(primero, "CERRADO"));
        assertEquals(200, cambiarEstadoBimestre(segundo, "ABIERTO"));
    }

    @Test
    @DisplayName("Bimestre fuera del año, solapado o con número repetido responde 400/409")
    void reglasDelBimestre() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        crearBimestre(anio, 1, "2026-03-02", "2026-05-15");

        assertEquals(400, crear(BIMESTRES_DE(anio), """
                {"numero":2,"fechaInicio":"2026-02-01","fechaFin":"2026-04-30"}""")
                .getResponse().getStatus(), "empieza antes del año");
        assertEquals(400, crear(BIMESTRES_DE(anio), """
                {"numero":2,"fechaInicio":"2026-11-01","fechaFin":"2026-12-31"}""")
                .getResponse().getStatus(), "termina después del año");
        assertEquals(409, crear(BIMESTRES_DE(anio), """
                {"numero":2,"fechaInicio":"2026-05-10","fechaFin":"2026-07-31"}""")
                .getResponse().getStatus(), "se solapa con el Bimestre 1");
        assertEquals(409, crear(BIMESTRES_DE(anio), """
                {"numero":1,"fechaInicio":"2026-08-03","fechaFin":"2026-10-15"}""")
                .getResponse().getStatus(), "el número de bimestre ya existe");
    }

    @Test
    @DisplayName("El bimestre trae su nombre derivado (Bimestre I) y su año")
    void bimestreTraeNombreDerivado() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");

        MvcResult creado = crear(BIMESTRES_DE(anio), """
                {"numero":1,"fechaInicio":"2026-03-02","fechaFin":"2026-05-15"}""");

        assertEquals(201, creado.getResponse().getStatus());
        String cuerpo = creado.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"nombre\":\"Bimestre I\""), "nombre derivado: " + cuerpo);
        assertTrue(cuerpo.contains("\"anioNombre\":"), "año del bimestre: " + cuerpo);
    }

    // ==================== Secciones ====================

    @Test
    @DisplayName("Grado fuera de 1..5 o letra inválida responden 400 (sólo secundaria)")
    void gradoFueraDeRangoResponde400() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");

        assertEquals(400, estadoDeSeccion(6, "A", anio, null), "grado 6 no existe en secundaria");
        assertEquals(400, estadoDeSeccion(0, "A", anio, null));
        assertEquals(400, estadoDeSeccion(1, "a", anio, null), "la letra va en mayúscula");
    }

    @Test
    @DisplayName("La sección trae etiqueta, año y tutor en su respuesta")
    void crearSeccionConTutor() throws Exception {
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long tutor = crearDocente("tutor@educa360.pe", "20000010");

        MvcResult resultado = crear(SECCIONES, seccionBody(3, "A", anio, tutor));

        assertEquals(201, resultado.getResponse().getStatus());
        String cuerpo = resultado.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"grado\":3"));
        assertTrue(cuerpo.contains("\"letra\":\"A\""));
        assertTrue(cuerpo.contains("\"aula\":\"A-101\""));
        assertTrue(cuerpo.contains("\"anioNombre\":"), "año de la sección: " + cuerpo);
        assertTrue(cuerpo.contains("\"tutorNombre\":\"Dana Perez\""), "el nombre del tutor debe viajar: " + cuerpo);
    }

    @Test
    @DisplayName("'grado+letra' es único DENTRO de un año; en otro año sí se puede repetir")
    void seccionDuplicadaEnElAnioResponde409() throws Exception {
        long anio2026 = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long anio2027 = crearAnio(2027, "Año Escolar 2027", "2027-03-01", "2027-12-17");
        assertEquals(201, estadoDeSeccion(3, "A", anio2026, null));

        assertEquals(409, estadoDeSeccion(3, "A", anio2026, null), "misma sección repetida en el mismo año");
        assertEquals(201, estadoDeSeccion(3, "A", anio2027, null), "las secciones se re-forman cada año");
    }

    @Test
    @DisplayName("Una sección con año inexistente responde 404")
    void seccionConAnioInexistenteResponde404() throws Exception {
        assertEquals(404, estadoDeSeccion(1, "A", 999L, null));
    }

    @Test
    @DisplayName("Una sección NO se puede mover de año (409)")
    void seccionNoSeMueveDeAnio() throws Exception {
        long anio2026 = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long anio2027 = crearAnio(2027, "Año Escolar 2027", "2027-03-01", "2027-12-17");
        long seccion = nuevaSeccion(3, "A", anio2026);

        assertEquals(409, conToken(put(SECCIONES + "/" + seccion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(seccionBody(3, "A", anio2027, null)), admin)
                .getResponse().getStatus(), "moverla arrastraría matrículas, asistencia y notas");

        assertEquals(200, conToken(put(SECCIONES + "/" + seccion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(seccionBody(3, "B", anio2026, null)), admin)
                .getResponse().getStatus(), "mismo año: cambiar letra sí se puede");
    }

    // ==================== Materias y habilitaciones ====================

    @Test
    @DisplayName("Dos materias con el mismo nombre se rechazan con 409")
    void materiaDuplicadaResponde409() throws Exception {
        crearMateria("Matematicas"); // la primera sí se crea (exige 201)
        MvcResult repetida = crear(MATERIAS, "{\"nombre\":\"Matematicas\"}");

        assertEquals(409, repetida.getResponse().getStatus());
    }

    @Test
    @DisplayName("PUT de habilitaciones reemplaza el conjunto completo de materias")
    void habilitarMateriasReemplazaElConjunto() throws Exception {
        long docente = crearDocente("doc1@educa360.pe", "20000011");
        long matematicas = crearMateria("Matematicas");
        long fisica = crearMateria("Fisica");

        assertEquals(200, habilitar(docente, "[%d,%d]".formatted(matematicas, fisica)));
        String ambas = listarMaterias(docente);
        assertTrue(ambas.contains("Matematicas") && ambas.contains("Fisica"));

        assertEquals(200, habilitar(docente, "[%d]".formatted(matematicas)), "un PUT pinta la hoja entera");
        String soloUna = listarMaterias(docente);
        assertTrue(soloUna.contains("Matematicas"));
        assertFalse(soloUna.contains("Fisica"), "las que no vinieron se quitan");

        assertEquals(404, habilitar(docente, "[999]"), "materia inexistente");
    }

    // ==================== Cursos ====================

    @Test
    @DisplayName("Crear un curso para un docente SIN la materia habilitada responde 409")
    void cursoSinHabilitacionResponde409() throws Exception {
        long docente = crearDocente("doc1@educa360.pe", "20000011");
        long matematicas = crearMateria("Matematicas");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion = nuevaSeccion(1, "A", anio);

        assertEquals(409, curso(docente, matematicas, seccion),
                "sin habilitación previa no se crea el curso");
    }

    @Test
    @DisplayName("Una materia sólo puede tener UN curso por sección (409 al repetir)")
    void cursoUnicoPorMateriaYSeccion() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long elva = crearDocente("doc2@educa360.pe", "20000012");
        long matematicas = crearMateria("Matematicas");
        long fisica = crearMateria("Fisica");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion = nuevaSeccion(3, "A", anio);

        habilitar(dana, "[%d,%d]".formatted(matematicas, fisica));
        habilitar(elva, "[%d]".formatted(fisica));

        assertEquals(201, curso(dana, matematicas, seccion));
        assertEquals(409, curso(dana, matematicas, seccion), "misma materia y sección");

        assertEquals(201, curso(elva, fisica, seccion), "otra materia en la misma sección sí");
        assertEquals(409, curso(dana, fisica, seccion), "la materia ya tiene curso en esa sección");
    }

    @Test
    @DisplayName("Una materia desactivada frena NUEVOS cursos")
    void materiaDesactivadaBloqueaNuevosCursos() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long matematicas = crearMateria("Matematicas");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion3A = nuevaSeccion(3, "A", anio);
        long seccion4B = nuevaSeccion(4, "B", anio);
        habilitar(dana, "[%d]".formatted(matematicas));

        assertEquals(201, curso(dana, matematicas, seccion3A), "habilitada y activa: pasa");

        MvcResult desactivada = conToken(patch(MATERIAS + "/" + matematicas + "/activa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activa\":false}"), admin);
        assertEquals(200, desactivada.getResponse().getStatus());

        assertEquals(409, curso(dana, matematicas, seccion4B),
                "reactivar la materia antes de crear el curso");
    }

    @Test
    @DisplayName("Eliminar un curso y repetir el borrado responde 404")
    void eliminarCursoYRepetirResponde404() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long matematicas = crearMateria("Matematicas");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion = nuevaSeccion(1, "A", anio);
        habilitar(dana, "[%d]".formatted(matematicas));
        long cursoId = nuevoCurso(dana, matematicas, seccion);

        MvcResult borrado = conToken(delete(CURSOS + "/" + cursoId), admin);
        assertEquals(204, borrado.getResponse().getStatus());

        assertEquals(404, conToken(delete(CURSOS + "/" + cursoId), admin)
                .getResponse().getStatus(), "ya no existe");
    }

    // ==================== Horario ====================

    @Test
    @DisplayName("El horario se pinta por hoja entera: el PUT reemplaza los bloques")
    void horarioReemplazaLaHojaEntera() throws Exception {
        long cursoId = cursoDeUnDocente("doc1@educa360.pe", "20000011", "Matematicas");

        assertEquals(200, putHorario(cursoId, """
                {"bloques":[{"dia":"LUNES","horaInicio":"08:00","horaFin":"09:00"},
                             {"dia":"MARTES","horaInicio":"08:00","horaFin":"09:00"}]}"""));
        String dos = getHorario(cursoId);
        assertTrue(dos.contains("LUNES") && dos.contains("MARTES"));

        assertEquals(200, putHorario(cursoId, """
                {"bloques":[{"dia":"LUNES","horaInicio":"10:00","horaFin":"11:00"}]}"""));
        String uno = getHorario(cursoId);
        assertTrue(uno.contains("10:00"));
        assertFalse(uno.contains("MARTES"), "los bloques que no vinieron se quitan");
    }

    @Test
    @DisplayName("Dos cursos de la MISMA sección no pueden solaparse (409)")
    void solapeEnLaSeccionResponde409() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long elva = crearDocente("doc2@educa360.pe", "20000012");
        long matematicas = crearMateria("Matematicas");
        long fisica = crearMateria("Fisica");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion = nuevaSeccion(3, "A", anio);
        habilitar(dana, "[%d]".formatted(matematicas));
        habilitar(elva, "[%d]".formatted(fisica));
        long cursoMatematicas = nuevoCurso(dana, matematicas, seccion);
        long cursoFisica = nuevoCurso(elva, fisica, seccion);

        assertEquals(200, putHorario(cursoMatematicas, """
                {"bloques":[{"dia":"LUNES","horaInicio":"08:00","horaFin":"09:00"}]}"""));
        assertEquals(409, putHorario(cursoFisica, """
                {"bloques":[{"dia":"LUNES","horaInicio":"08:30","horaFin":"09:30"}]}"""),
                "la sección ya está ocupada a esa hora");
        assertEquals(200, putHorario(cursoFisica, """
                {"bloques":[{"dia":"LUNES","horaInicio":"09:00","horaFin":"10:00"}]}"""),
                "consecutivo (09:00 fin / 09:00 inicio) no es solape");
    }

    @Test
    @DisplayName("El mismo docente no puede estar en dos cursos a la misma hora (409)")
    void solapeEnElDocenteResponde409() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long matematicas = crearMateria("Matematicas");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion3A = nuevaSeccion(3, "A", anio);
        long seccion4B = nuevaSeccion(4, "B", anio);
        habilitar(dana, "[%d]".formatted(matematicas));
        long en3A = nuevoCurso(dana, matematicas, seccion3A);
        long en4B = nuevoCurso(dana, matematicas, seccion4B); // misma materia, otra sección: permitido

        assertEquals(200, putHorario(en3A, """
                {"bloques":[{"dia":"MARTES","horaInicio":"10:00","horaFin":"11:00"}]}"""));
        assertEquals(409, putHorario(en4B, """
                {"bloques":[{"dia":"MARTES","horaInicio":"10:30","horaFin":"11:30"}]}"""),
                "el docente no puede dar dos clases a la vez");
        assertEquals(200, putHorario(en4B, """
                {"bloques":[{"dia":"MARTES","horaInicio":"11:00","horaFin":"12:00"}]}"""));
    }

    @Test
    @DisplayName("Re-PUT idéntico de la hoja es 200 (no 409): repetir no cambia nada")
    void rePutIdenticoDelHorarioEsValido() throws Exception {
        long cursoId = cursoDeUnDocente("doc1@educa360.pe", "20000011", "Matematicas");
        String hoja = """
                {"bloques":[{"dia":"LUNES","horaInicio":"08:00","horaFin":"09:00"},
                             {"dia":"MARTES","horaInicio":"08:00","horaFin":"09:00"}]}""";

        assertEquals(200, putHorario(cursoId, hoja));
        MvcResult segunda = conToken(put(CURSOS + "/" + cursoId + "/horario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(hoja), admin);
        assertEquals(200, segunda.getResponse().getStatus(),
                "repetir la hoja idéntica debe ser un no-op de 200: "
                        + segunda.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Bloque con horas invertidas o día inválido responde 400")
    void horarioInvalidoResponde400() throws Exception {
        long cursoId = cursoDeUnDocente("doc1@educa360.pe", "20000011", "Matematicas");

        assertEquals(400, putHorario(cursoId, """
                {"bloques":[{"dia":"LUNES","horaInicio":"09:00","horaFin":"08:00"}]}"""),
                "fin antes que inicio");
        assertEquals(400, putHorario(cursoId, """
                {"bloques":[{"dia":"DOMINGO","horaInicio":"08:00","horaFin":"09:00"}]}"""),
                "día fuera del enum");
    }

    // ==================== Zonas por rol ====================

    @Test
    @DisplayName("El DOCENTE ve SÓLO sus cursos desde /docente/cursos")
    void docenteVeSoloSusCursos() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long elva = crearDocente("doc2@educa360.pe", "20000012");
        long matematicas = crearMateria("Matematicas");
        long fisica = crearMateria("Fisica");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion3A = nuevaSeccion(3, "A", anio);
        long seccion4B = nuevaSeccion(4, "B", anio);
        habilitar(dana, "[%d]".formatted(matematicas));
        habilitar(elva, "[%d]".formatted(fisica));
        nuevoCurso(dana, matematicas, seccion3A);
        nuevoCurso(elva, fisica, seccion4B);

        String tokenDana = loginYToken("doc1@educa360.pe", "Docente1234");
        String cursos = conToken(get("/api/v1/docente/cursos"), tokenDana)
                .getResponse().getContentAsString();
        assertTrue(cursos.contains("Matematicas"), "ve el suyo: " + cursos);
        assertFalse(cursos.contains("Fisica"), "no ve los cursos del otro docente");

        assertEquals(403, conToken(get("/api/v1/docente/cursos"), admin).getResponse().getStatus(),
                "la zona /docente es sólo para DOCENTE: ADMIN no es docente");
    }

    @Test
    @DisplayName("El DOCENTE consulta el horario de SU curso; el de otro docente responde 403")
    void docenteConsultaHorarioDeSuCurso() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long elva = crearDocente("doc2@educa360.pe", "20000012");
        long matematicas = crearMateria("Matematicas");
        long fisica = crearMateria("Fisica");
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion = nuevaSeccion(3, "A", anio);
        habilitar(dana, "[%d]".formatted(matematicas));
        habilitar(elva, "[%d]".formatted(fisica));
        long cursoDana = nuevoCurso(dana, matematicas, seccion);
        long cursoElva = nuevoCurso(elva, fisica, seccion);
        assertEquals(200, putHorario(cursoDana, """
                {"bloques":[{"dia":"LUNES","horaInicio":"08:00","horaFin":"09:00"}]}"""));

        String tokenDana = loginYToken("doc1@educa360.pe", "Docente1234");

        MvcResult propio = conToken(get("/api/v1/docente/cursos/" + cursoDana + "/horario"), tokenDana);
        assertEquals(200, propio.getResponse().getStatus());
        assertTrue(propio.getResponse().getContentAsString().contains("LUNES"),
                "ve el horario de su curso");

        assertEquals(403, conToken(get("/api/v1/docente/cursos/" + cursoElva + "/horario"), tokenDana)
                .getResponse().getStatus(), "no puede ver el horario de un curso ajeno");
        assertEquals(404, conToken(get("/api/v1/docente/cursos/999999/horario"), tokenDana)
                .getResponse().getStatus(), "curso inexistente");
    }

    @Test
    @DisplayName("GET /admin/docentes expone el id de PERFIL con título y materias")
    void listarDocentesExponeElPerfilId() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long matematicas = crearMateria("Matematicas");
        habilitar(dana, "[%d]".formatted(matematicas));

        String cuerpo = conToken(get("/api/v1/admin/docentes"), admin)
                .getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"titulo\":\"Lic. en Matematicas\""), "el título debe viajar: " + cuerpo);
        assertTrue(cuerpo.contains("Matematicas"), "las materias habilitadas deben venir");
        assertTrue(cuerpo.contains("\"id\":%d".formatted(dana)),
                "el front necesita el id de perfil para /admin/docentes/{id}/materias");
    }

    @Test
    @DisplayName("/auth/me trae el perfil del DOCENTE (título y materias) en una sola llamada")
    void meDelDocenteTraeElPerfil() throws Exception {
        long dana = crearDocente("doc1@educa360.pe", "20000011");
        long matematicas = crearMateria("Matematicas");
        habilitar(dana, "[%d]".formatted(matematicas));

        String tokenDana = loginYToken("doc1@educa360.pe", "Docente1234");
        String cuerpo = conToken(get("/api/v1/auth/me"), tokenDana)
                .getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"rol\":\"DOCENTE\""));
        assertTrue(cuerpo.contains("\"titulo\":\"Lic. en Matematicas\""), "el perfil debe venir: " + cuerpo);
        assertTrue(cuerpo.contains("Matematicas"), "con sus materias habilitadas");

        String meAdmin = conToken(get("/api/v1/auth/me"), admin)
                .getResponse().getContentAsString();
        assertTrue(meAdmin.contains("\"perfil\":null"), "ADMIN no tiene perfil propio todavía: " + meAdmin);
    }

    // ==================== RBAC ====================

    @Test
    @DisplayName("El catálogo es de ADMIN: 401 sin token y 403 con token de ESTUDIANTE")
    void rbacDelCatalogo() throws Exception {
        assertEquals(401, mockMvc.perform(get(ANIOS)).andReturn().getResponse().getStatus());

        String estudiante = crearYTokenearEstudiante();
        assertEquals(403, conToken(get(ANIOS), estudiante).getResponse().getStatus());

        assertEquals(200, conToken(get(ANIOS), admin).getResponse().getStatus());
    }

    // ==================== Helpers ====================

    private static String BIMESTRES_DE(long anioId) {
        return ANIOS + "/" + anioId + "/bimestres";
    }

    private String anioBody(int anio, String nombre, String inicio, String fin) {
        return """
                {"anio":%d,"nombre":"%s","fechaInicio":"%s","fechaFin":"%s"}"""
                .formatted(anio, nombre, inicio, fin);
    }

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

    private long crearAnio(int anio, String nombre, String inicio, String fin) throws Exception {
        return idDe(crear(ANIOS, anioBody(anio, nombre, inicio, fin)));
    }

    private int cambiarEstadoAnio(long id, String estado) throws Exception {
        return conToken(patch(ANIOS + "/" + id + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"%s\"}".formatted(estado)), admin)
                .getResponse().getStatus();
    }

    private long crearBimestre(long anioId, int numero, String inicio, String fin) throws Exception {
        return idDe(crear(BIMESTRES_DE(anioId), """
                {"numero":%d,"fechaInicio":"%s","fechaFin":"%s"}"""
                .formatted(numero, inicio, fin)));
    }

    private int cambiarEstadoBimestre(long id, String estado) throws Exception {
        return conToken(patch("/api/v1/admin/bimestres/" + id + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"%s\"}".formatted(estado)), admin)
                .getResponse().getStatus();
    }

    /** Crea la sección y devuelve su id (exige 201). */
    private long nuevaSeccion(int grado, String letra, long anioId) throws Exception {
        return idDe(crear(SECCIONES, seccionBody(grado, letra, anioId, null)));
    }

    /** Status de la creación, para expectativas 400/404/409. */
    private int estadoDeSeccion(int grado, String letra, long anioId, Long tutorId) throws Exception {
        return crear(SECCIONES, seccionBody(grado, letra, anioId, tutorId))
                .getResponse().getStatus();
    }

    private String seccionBody(int grado, String letra, long anioId, Long tutorId) {
        return """
                {"grado":%d,"letra":"%s","aula":"A-101","tutorId":%s,"anioId":%d}
                """.formatted(grado, letra, tutorId == null ? "null" : tutorId, anioId);
    }

    private long crearMateria(String nombre) throws Exception {
        return idDe(crear(MATERIAS, "{\"nombre\":\"%s\"}".formatted(nombre)));
    }

    private long crearDocente(String email, String dni) throws Exception {
        idDe(crear("/api/v1/admin/docentes", """
                {"usuario":{"email":"%s","password":"Docente1234","nombres":"Dana","apellidos":"Perez",
                            "dni":"%s"},
                 "perfil":{"titulo":"Lic. en Matematicas"}}
                """.formatted(email, dni)));
        return docenteRepository.findAll().stream()
                .filter(d -> d.getUser().getEmail().equals(email))
                .findFirst().orElseThrow().getId();
    }

    private int habilitar(long docenteId, String materiaIdsJson) throws Exception {
        return conToken(put("/api/v1/admin/docentes/" + docenteId + "/materias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materiaIds\":" + materiaIdsJson + "}"), admin)
                .getResponse().getStatus();
    }

    private String listarMaterias(long docenteId) throws Exception {
        return conToken(get("/api/v1/admin/docentes/" + docenteId + "/materias"), admin)
                .getResponse().getContentAsString();
    }

    /** POST de curso; devuelve el status (para expectativas 201/409). */
    private int curso(long docenteId, long materiaId, long seccionId) throws Exception {
        return crear(CURSOS, """
                {"docenteId":%d,"materiaId":%d,"seccionId":%d}"""
                .formatted(docenteId, materiaId, seccionId))
                .getResponse().getStatus();
    }

    /** POST de curso que exige 201 y devuelve el id. */
    private long nuevoCurso(long docenteId, long materiaId, long seccionId) throws Exception {
        return idDe(crear(CURSOS, """
                {"docenteId":%d,"materiaId":%d,"seccionId":%d}"""
                .formatted(docenteId, materiaId, seccionId)));
    }

    /** Monta el camino completo y devuelve el curso: docente + habilitación + materia + sección. */
    private long cursoDeUnDocente(String email, String dni, String materia) throws Exception {
        long docente = crearDocente(email, dni);
        long materiaId = crearMateria(materia);
        long anio = crearAnio(2026, "Año Escolar 2026", "2026-03-02", "2026-12-18");
        long seccion = nuevaSeccion(3, "A", anio);
        habilitar(docente, "[%d]".formatted(materiaId));
        return nuevoCurso(docente, materiaId, seccion);
    }

    private int putHorario(long cursoId, String body) throws Exception {
        return conToken(put(CURSOS + "/" + cursoId + "/horario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body), admin)
                .getResponse().getStatus();
    }

    private String getHorario(long cursoId) throws Exception {
        return conToken(get(CURSOS + "/" + cursoId + "/horario"), admin)
                .getResponse().getContentAsString();
    }

    /** Id de un recurso recién creado: exige que la creación haya sido 201. */
    private long idDe(MvcResult resultado) throws Exception {
        assertEquals(201, resultado.getResponse().getStatus(),
                "creación inesperada: " + resultado.getResponse().getContentAsString());
        return idDeSilencioso(resultado);
    }

    private long idDeSilencioso(MvcResult resultado) throws Exception {
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

    private String crearYTokenearEstudiante() throws Exception {
        MvcResult creado = crear("/api/v1/secretaria/estudiantes", """
                {"usuario":{"email":"alu@educa360.pe","password":"Alumno1234","nombres":"Alma",
                            "apellidos":"Luna","dni":"30000001"},
                 "perfil":{"fechaNacimiento":"2011-03-03"}}""");
        assertEquals(201, creado.getResponse().getStatus());
        return loginYToken("alu@educa360.pe", "Alumno1234");
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
