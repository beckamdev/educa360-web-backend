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
import com.educa360.backend.repository.MaterialClaseRepository;
import com.educa360.backend.repository.MateriaRepository;
import com.educa360.backend.repository.MatriculaRepository;
import com.educa360.backend.repository.SeccionRepository;
import com.educa360.backend.repository.SecretariaRepository;
import com.educa360.backend.repository.TicketJustificacionRepository;
import com.educa360.backend.repository.UserRepository;
import com.educa360.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Tests del Módulo 7: justificaciones de inasistencia.
 *
 * Reglas que protege el suite:
 * - sólo el apoderado vinculado crea tickets de su hijo;
 * - la fecha no es futura y, si hay bloque, debe ser de su sección y su día;
 * - no se solapan dos tickets activos para el mismo alumno y fecha;
 * - sólo Secretaría resuelve y sólo desde PENDIENTE;
 * - aprobar convierte las FALTA del alcance en JUSTIFICADA;
 * - rechazar no toca la asistencia;
 * - una falta registrada después de aprobar nace JUSTIFICADA;
 * - ADMIN no entra a la bandeja de Secretaría.
 *
 * PostgreSQL efímera (Testcontainers): no toca la BD de desarrollo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class JustificacionApiTest {

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

    @Autowired
    private TicketJustificacionRepository ticketRepository;

    @Autowired
    private MaterialClaseRepository materialClaseRepository;

    @MockitoBean
    private StorageService storageService;

    private String admin;

    @BeforeEach
    void empezarDeCero() throws Exception {
        // Orden de borrado por las FK: lo hijo primero
        materialClaseRepository.deleteAll();
        asistenciaRepository.deleteAll();
        ticketRepository.deleteAll();
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
        when(storageService.signedUrl(anyString(), anyString(), any()))
                .thenReturn("https://signed.local/archivo");
    }

    @Test
    @DisplayName("Flujo: apoderado justifica, Secretaría aprueba y la falta pasa a JUSTIFICADA")
    void flujoDeJustificacion() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, e.alumno(), "MADRE", true));
        long secretaria = crearSecretaria("sofia@educa360.pe", "21000001");
        assertNotNull(secretaria);

        // El docente marca FALTA.
        assertEquals(200, registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), clase,
                "[{\"matriculaId\":%d,\"asistio\":false}]".formatted(e.matricula()))
                .getResponse().getStatus());

        // El apoderado crea un ticket de día completo.
        MvcResult creado = crearTicket("lucia@educa360.pe", e.alumno(), """
                {"fechaInasistencia":"%s","bloqueId":null,"motivo":"  Cita médica  "}""".formatted(clase));
        assertEquals(201, creado.getResponse().getStatus(),
                creado.getResponse().getContentAsString());
        long ticket = idDe(creado);
        String cuerpoTicket = creado.getResponse().getContentAsString();
        assertTrue(cuerpoTicket.contains("\"estado\":\"PENDIENTE\""), cuerpoTicket);
        assertTrue(cuerpoTicket.contains("\"motivo\":\"Cita médica\""), cuerpoTicket);
        assertTrue(cuerpoTicket.contains("\"bloqueId\":null"), cuerpoTicket);

        // El apoderado lo ve en su historial; otro hijo no vinculado no.
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");
        String historial = conToken(get("/api/v1/apoderado/hijos/" + e.alumno()
                + "/tickets"), tokenMama).getResponse().getContentAsString();
        assertTrue(historial.contains("\"id\":" + ticket), historial);
        assertEquals(200, conToken(get("/api/v1/apoderado/hijos/" + e.alumno()
                + "/tickets?estado=PENDIENTE&anioId=" + e.anio()), tokenMama)
                .getResponse().getStatus());
        assertEquals(404, conToken(get("/api/v1/apoderado/hijos/" + 99999
                + "/tickets"), tokenMama).getResponse().getStatus());

        // Secretaría ve la bandeja y el detalle.
        String tokenSecretaria = loginYToken("sofia@educa360.pe", "Secreta1234");
        String bandeja = conToken(get("/api/v1/secretaria/tickets?estado=PENDIENTE&fecha="
                + clase), tokenSecretaria).getResponse().getContentAsString();
        assertTrue(bandeja.contains("\"id\":" + ticket), bandeja);
        assertEquals(200, conToken(get("/api/v1/secretaria/tickets/" + ticket),
                tokenSecretaria).getResponse().getStatus());

        // Aprobar: la falta pasa a JUSTIFICADA y el resumen sube a 100%.
        MvcResult resuelto = resolverTicket(tokenSecretaria, ticket, "APROBADO",
                "  Documento válido  ");
        assertEquals(200, resuelto.getResponse().getStatus(),
                resuelto.getResponse().getContentAsString());
        String cuerpoResuelto = resuelto.getResponse().getContentAsString();
        assertTrue(cuerpoResuelto.contains("\"estado\":\"APROBADO\""), cuerpoResuelto);
        assertTrue(cuerpoResuelto.contains("\"resueltoPorId\":" + secretaria), cuerpoResuelto);
        assertTrue(cuerpoResuelto.contains("\"observacionesAdmin\":\"Documento válido\""),
                cuerpoResuelto);

        String tokenAlma = loginYToken("alma@educa360.pe", "Alumno1234");
        String resumen = conToken(get("/api/v1/estudiante/asistencias"), tokenAlma)
                .getResponse().getContentAsString();
        assertTrue(resumen.contains("\"estado\":\"JUSTIFICADA\""), resumen);
        assertTrue(resumen.contains("\"justificadas\":1"), resumen);
        assertTrue(resumen.contains("\"faltas\":0"), resumen);
        assertTrue(resumen.contains("\"porcentaje\":100.00"), resumen);
        assertTrue(resumen.contains("\"ticketId\":" + ticket), resumen);

        // No se resuelve dos veces ni se crea otro ticket activo ese día.
        assertEquals(409, resolverTicket(tokenSecretaria, ticket, "RECHAZADO", null)
                .getResponse().getStatus());
        assertEquals(409, crearTicket("lucia@educa360.pe", e.alumno(), """
                {"fechaInasistencia":"%s","bloqueId":null,"motivo":"Otra vez"}""".formatted(clase)).getResponse().getStatus());

        // RBAC: cada rol en su zona; ADMIN no resuelve ni lee la bandeja.
        assertEquals(403, conToken(get("/api/v1/secretaria/tickets"), tokenAlma)
                .getResponse().getStatus(), "un alumno no entra a Secretaría");
        assertEquals(403, resolverTicket(tokenMama, ticket, "APROBADO", null)
                .getResponse().getStatus(), "un apoderado no resuelve");
        assertEquals(403, conToken(get("/api/v1/secretaria/tickets"), admin)
                .getResponse().getStatus(), "ADMIN no entra a la bandeja de Secretaría");
        assertEquals(403, conToken(get("/api/v1/apoderado/hijos/" + e.alumno()
                + "/tickets"), tokenSecretaria).getResponse().getStatus(),
                "Secretaría no entra a la zona del apoderado");
    }

    @Test
    @DisplayName("Una falta registrada después de aprobar el ticket nace JUSTIFICADA")
    void ticketAntesDeAsistencia() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);
        long beto = crearEstudiante("beto@educa360.pe", "30000002", "Beto");
        long matriculaBeto = matricular(beto, e.seccion(), primerDia(e));
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, beto, "MADRE", true));
        crearSecretaria("sofia@educa360.pe", "21000001");
        String tokenSecretaria = loginYToken("sofia@educa360.pe", "Secreta1234");

        // Se aprueba el ticket antes de que el docente registre la clase.
        long ticket = idDe(crearTicket("lucia@educa360.pe", beto, """
                {"fechaInasistencia":"%s","bloqueId":%d,"motivo":"Cita médica"}"""
                .formatted(clase, e.bloque())));
        assertEquals(200, resolverTicket(tokenSecretaria, ticket, "APROBADO", null)
                .getResponse().getStatus());

        // El docente registra la falta después: debe nacer JUSTIFICADA.
        MvcResult registro = registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), clase,
                "[{\"matriculaId\":%d,\"asistio\":false}]".formatted(matriculaBeto));
        assertEquals(200, registro.getResponse().getStatus(),
                registro.getResponse().getContentAsString());
        assertTrue(registro.getResponse().getContentAsString()
                .contains("\"estado\":\"JUSTIFICADA\""),
                registro.getResponse().getContentAsString());

        String tokenBeto = loginYToken("beto@educa360.pe", "Alumno1234");
        String resumen = conToken(get("/api/v1/estudiante/asistencias"), tokenBeto)
                .getResponse().getContentAsString();
        assertTrue(resumen.contains("\"justificadas\":1"), resumen);
        assertTrue(resumen.contains("\"porcentaje\":100.00"), resumen);
    }

    @Test
    @DisplayName("Rechazar no toca la asistencia y las validaciones del ticket responden 400/404")
    void validacionesYRechazo() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);
        long carla = crearEstudiante("carla@educa360.pe", "30000003", "Carla");
        long matriculaCarla = matricular(carla, e.seccion(), primerDia(e));
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, carla, "MADRE", true));
        crearSecretaria("sofia@educa360.pe", "21000001");
        String tokenSecretaria = loginYToken("sofia@educa360.pe", "Secreta1234");
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");

        // Fecha futura.
        LocalDate futura = clase;
        while (!futura.isAfter(LocalDate.now())) {
            futura = futura.plusWeeks(1);
        }
        assertEquals(400, crearTicket("lucia@educa360.pe", carla, """
                {"fechaInasistencia":"%s","bloqueId":null,"motivo":"Cita"}"""
                .formatted(futura)).getResponse().getStatus());

        // Bloque de otra sección.
        long seccionB = nuevaSeccion(3, "B", e.anio());
        long alumnoB = crearEstudiante("beto@educa360.pe", "30000002", "Beto");
        matricular(alumnoB, seccionB, primerDia(e));
        long docenteB = crearDocente("otto@educa360.pe", "20000002");
        long materiaB = crearMateria("Comunicación");
        assertEquals(200, habilitar(docenteB, "[" + materiaB + "]"));
        long cursoB = nuevoCurso(docenteB, materiaB, seccionB);
        long bloqueB = crearBloque(cursoB, diaDe(clase), "08:00", "09:00");
        assertEquals(404, crearTicket("lucia@educa360.pe", carla, """
                {"fechaInasistencia":"%s","bloqueId":%d,"motivo":"Cita"}"""
                .formatted(clase, bloqueB)).getResponse().getStatus(),
                "el bloque es de otra sección");

        // Fecha que no es el día del bloque.
        assertEquals(400, crearTicket("lucia@educa360.pe", carla, """
                {"fechaInasistencia":"%s","bloqueId":%d,"motivo":"Cita"}"""
                .formatted(clase.plusDays(1), e.bloque())).getResponse().getStatus(),
                "la fecha no es el día del bloque");

        // Motivo vacío.
        assertEquals(400, crearTicket("lucia@educa360.pe", carla, """
                {"fechaInasistencia":"%s","bloqueId":null,"motivo":"   "}"""
                .formatted(clase)).getResponse().getStatus());

        // Hijo no vinculado.
        long otro = crearEstudiante("dani@educa360.pe", "30000004", "Dani");
        assertEquals(404, crearTicket("lucia@educa360.pe", otro, """
                {"fechaInasistencia":"%s","bloqueId":null,"motivo":"Cita"}"""
                .formatted(clase)).getResponse().getStatus());

        // Rechazo: la falta sigue FALTA y el alumno conserva 0%.
        assertEquals(200, registrarAsistencia(e.tokenDocente(), e.curso(), e.bloque(), clase,
                "[{\"matriculaId\":%d,\"asistio\":false}]".formatted(matriculaCarla))
                .getResponse().getStatus());
        long ticket = idDe(crearTicket("lucia@educa360.pe", carla, """
                {"fechaInasistencia":"%s","bloqueId":null,"motivo":"Cita"}"""
                .formatted(clase)));
        assertEquals(200, resolverTicket(tokenSecretaria, ticket, "RECHAZADO", "Sin sustento")
                .getResponse().getStatus());

        String tokenCarla = loginYToken("carla@educa360.pe", "Alumno1234");
        String resumen = conToken(get("/api/v1/estudiante/asistencias"), tokenCarla)
                .getResponse().getContentAsString();
        assertTrue(resumen.contains("\"estado\":\"FALTA\""), resumen);
        assertTrue(resumen.contains("\"faltas\":1"), resumen);
        assertTrue(resumen.contains("\"porcentaje\":0.00"), resumen);

        // La bandeja filtra por estado RECHAZADO.
        String rechazados = conToken(get("/api/v1/secretaria/tickets?estado=RECHAZADO"),
                tokenSecretaria).getResponse().getContentAsString();
        assertTrue(rechazados.contains("\"id\":" + ticket), rechazados);
        assertTrue(rechazados.contains("\"observacionesAdmin\":\"Sin sustento\""), rechazados);

        // Un ticket RECHAZADO no bloquea uno nuevo (ya no está activo).
        assertEquals(201, crearTicket("lucia@educa360.pe", carla, """
                {"fechaInasistencia":"%s","bloqueId":null,"motivo":"Nuevo sustento"}"""
                .formatted(clase)).getResponse().getStatus());
        assertNotNull(tokenMama);
    }

    @Test
    @DisplayName("M8: el material nace no publicado, se publica, descarga y da de baja")
    void flujoDeMateriales() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);
        String tokenAlma = loginYToken("alma@educa360.pe", "Alumno1234");

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "guia.pdf", "application/pdf", "%PDF-1.4".getBytes());
        MvcResult creado = conToken(multipart(DOCENTE + "/cursos/" + e.curso() + "/materiales")
                .file(archivo)
                .param("titulo", "Guía de fracciones"), e.tokenDocente());
        assertEquals(201, creado.getResponse().getStatus(),
                creado.getResponse().getContentAsString());
        long material = idDe(creado);
        assertTrue(creado.getResponse().getContentAsString()
                .contains("\"publicado\":false"), creado.getResponse().getContentAsString());

        // El alumno no ve un material sin publicar.
        String listaAlumno = conToken(get("/api/v1/estudiante/cursos/" + e.curso()
                + "/materiales"), tokenAlma).getResponse().getContentAsString();
        assertTrue(!listaAlumno.contains("\"id\":" + material), listaAlumno);

        // ADMIN sí lo ve, incluso sin publicar.
        String listaAdmin = conToken(get("/api/v1/admin/cursos/" + e.curso()
                + "/materiales"), admin).getResponse().getContentAsString();
        assertTrue(listaAdmin.contains("\"id\":" + material), listaAdmin);

        assertEquals(200, conToken(patch(DOCENTE + "/materiales/" + material + "/publicacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicado\":true}"), e.tokenDocente())
                .getResponse().getStatus());

        listaAlumno = conToken(get("/api/v1/estudiante/cursos/" + e.curso()
                + "/materiales"), tokenAlma).getResponse().getContentAsString();
        assertTrue(listaAlumno.contains("\"id\":" + material), listaAlumno);
        assertTrue(listaAlumno.contains("\"publicado\":true"), listaAlumno);

        MvcResult descarga = conToken(get("/api/v1/materiales/" + material + "/download"),
                tokenAlma);
        assertEquals(200, descarga.getResponse().getStatus(),
                descarga.getResponse().getContentAsString());
        assertTrue(descarga.getResponse().getContentAsString()
                .contains("https://signed.local/archivo"),
                descarga.getResponse().getContentAsString());

        assertEquals(204, conToken(delete(DOCENTE + "/materiales/" + material),
                e.tokenDocente()).getResponse().getStatus());

        listaAlumno = conToken(get("/api/v1/estudiante/cursos/" + e.curso()
                + "/materiales"), tokenAlma).getResponse().getContentAsString();
        assertTrue(!listaAlumno.contains("\"id\":" + material), listaAlumno);
        assertEquals(404, conToken(get("/api/v1/materiales/" + material + "/download"),
                tokenAlma).getResponse().getStatus());
    }

    @Test
    @DisplayName("M8: el apoderado adjunta sustento y Secretaría lo descarga")
    void flujoSustento() throws Exception {
        LocalDate clase = clasePasada();
        Escenario e = prepararEscenario(clase);
        long mama = crearApoderado("lucia@educa360.pe", "40000001", "Lucia");
        assertEquals(201, vincular(mama, e.alumno(), "MADRE", true));
        crearSecretaria("sofia@educa360.pe", "21000001");
        String tokenMama = loginYToken("lucia@educa360.pe", "Familia1234");
        String tokenSecretaria = loginYToken("sofia@educa360.pe", "Secreta1234");

        long ticket = idDe(crearTicket("lucia@educa360.pe", e.alumno(), """
                {"fechaInasistencia":"%s","bloqueId":null,"motivo":"Cita médica"}"""
                .formatted(clase)));

        MockMultipartFile sustento = new MockMultipartFile(
                "archivo", "certificado.pdf", "application/pdf", "%PDF-1.4".getBytes());
        MvcResult subido = conToken(multipart("/api/v1/apoderado/tickets/" + ticket
                + "/sustento").file(sustento), tokenMama);
        assertEquals(200, subido.getResponse().getStatus(),
                subido.getResponse().getContentAsString());
        assertTrue(subido.getResponse().getContentAsString()
                .contains("\"storagePath\":"), subido.getResponse().getContentAsString());

        assertEquals(200, conToken(get("/api/v1/apoderado/tickets/" + ticket + "/sustento"),
                tokenMama).getResponse().getStatus());
        assertEquals(200, conToken(get("/api/v1/secretaria/tickets/" + ticket + "/sustento"),
                tokenSecretaria).getResponse().getStatus());

        // Un archivo no permitido no reemplaza el sustento.
        MockMultipartFile exe = new MockMultipartFile(
                "archivo", "virus.exe", "application/octet-stream", "MZ".getBytes());
        assertEquals(400, conToken(multipart("/api/v1/apoderado/tickets/" + ticket
                + "/sustento").file(exe), tokenMama).getResponse().getStatus());

        // Resuelto el ticket, ya no se puede adjuntar otro sustento.
        assertEquals(200, resolverTicket(tokenSecretaria, ticket, "APROBADO", null)
                .getResponse().getStatus());
        assertEquals(409, conToken(multipart("/api/v1/apoderado/tickets/" + ticket
                + "/sustento").file(sustento), tokenMama).getResponse().getStatus());
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

    private MvcResult conToken(MockMultipartHttpServletRequestBuilder peticion, String token)
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

    private MvcResult crearTicket(String email, long hijoId, String body) throws Exception {
        String token = loginYToken(email, "Familia1234");
        return conToken(post("/api/v1/apoderado/hijos/" + hijoId + "/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body), token);
    }

    private MvcResult resolverTicket(String token, long ticketId, String estado,
                                     String observaciones) throws Exception {
        String json = observaciones == null
                ? "{\"estado\":\"%s\"}".formatted(estado)
                : "{\"estado\":\"%s\",\"observacionesAdmin\":\"%s\"}"
                        .formatted(estado, observaciones);
        return conToken(patch("/api/v1/secretaria/tickets/" + ticketId + "/resolver")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json), token);
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
