package com.educa360.backend.service;

import com.educa360.backend.dto.ArchivoDescargaResponse;
import com.educa360.backend.dto.MaterialResponse;
import com.educa360.backend.dto.MaterialUpdateRequest;
import com.educa360.backend.entity.AnioEscolar;
import com.educa360.backend.entity.Bimestre;
import com.educa360.backend.entity.Contenido;
import com.educa360.backend.entity.Curso;
import com.educa360.backend.entity.Docente;
import com.educa360.backend.entity.Estudiante;
import com.educa360.backend.entity.Materia;
import com.educa360.backend.entity.MaterialClase;
import com.educa360.backend.entity.Matricula;
import com.educa360.backend.entity.Seccion;
import com.educa360.backend.entity.Unidad;
import com.educa360.backend.entity.User;
import com.educa360.backend.exception.ConflictException;
import com.educa360.backend.exception.NotFoundException;
import com.educa360.backend.repository.ApoderadoEstudianteRepository;
import com.educa360.backend.repository.ApoderadoRepository;
import com.educa360.backend.repository.ContenidoRepository;
import com.educa360.backend.repository.CursoRepository;
import com.educa360.backend.repository.DocenteRepository;
import com.educa360.backend.repository.EstudianteRepository;
import com.educa360.backend.repository.MaterialClaseRepository;
import com.educa360.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock private MaterialClaseRepository materialRepository;
    @Mock private CursoRepository cursoRepository;
    @Mock private ContenidoRepository contenidoRepository;
    @Mock private DocenteRepository docenteRepository;
    @Mock private UserRepository userRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private ApoderadoRepository apoderadoRepository;
    @Mock private ApoderadoEstudianteRepository vinculoRepository;
    @Mock private MatriculaService matriculaService;
    @Mock private StorageService storageService;
    @Mock private ArchivoValidator archivoValidator;

    private MaterialService servicio;
    private User userDocente;
    private Docente docente;
    private User userEstudiante;
    private Estudiante estudiante;
    private AnioEscolar anio;
    private Seccion seccion;
    private Curso curso;
    private Matricula matricula;
    private Unidad unidad;
    private Contenido contenido;

    @BeforeEach
    void setUp() {
        servicio = new MaterialService(materialRepository, cursoRepository,
                contenidoRepository, docenteRepository, userRepository,
                estudianteRepository, apoderadoRepository, vinculoRepository,
                matriculaService, storageService, archivoValidator);

        userDocente = user("dana@educa360.pe", User.Role.DOCENTE, "Dana", "20000001");
        docente = new Docente(userDocente, "Licenciada");
        docente.setId(1L);
        userEstudiante = user("alma@educa360.pe", User.Role.ESTUDIANTE, "Alma", "30000001");
        estudiante = new Estudiante(userEstudiante, LocalDate.of(2011, 3, 3));
        estudiante.setId(60L);
        anio = new AnioEscolar(2026, "Año Escolar 2026", LocalDate.of(2026, 3, 2));
        anio.setId(10L);
        seccion = new Seccion(3, "A", "A-101", null, anio);
        seccion.setId(30L);
        Materia materia = new Materia("Matemática", null);
        materia.setId(20L);
        curso = new Curso(docente, materia, seccion);
        curso.setId(40L);
        matricula = new Matricula(estudiante, anio, seccion, LocalDate.of(2026, 1, 10));
        matricula.setId(70L);
        Bimestre bimestre = new Bimestre(anio, 1,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 15));
        bimestre.setId(50L);
        unidad = new Unidad(curso, bimestre, 1, "Fracciones");
        unidad.setId(80L);
        contenido = new Contenido(unidad, 1, "Suma de fracciones");
        contenido.setId(90L);
    }

    @Test
    @DisplayName("Crear material sube el archivo y lo deja NO publicado")
    void creaMaterialNoPublicado() {
        prepararDocente();
        ArchivoValidator.ArchivoValidado validado = new ArchivoValidator.ArchivoValidado(
                "guia.pdf", "guia.pdf", "application/pdf", 8,
                "%PDF-1.4".getBytes());
        when(archivoValidator.validarMaterial(any())).thenReturn(validado);
        when(materialRepository.saveAndFlush(any(MaterialClase.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MaterialResponse respuesta = servicio.crear("dana@educa360.pe", 40L,
                "  Guía  ", "  Fracciones  ", null,
                new MockMultipartFile("archivo", "guia.pdf", "application/pdf",
                        "%PDF-1.4".getBytes()));

        assertEquals("Guía", respuesta.titulo());
        assertEquals("Fracciones", respuesta.descripcion());
        assertFalse(respuesta.publicado());
        assertEquals("application/pdf", respuesta.mimeType());
        verify(storageService).upload(eq(StorageService.BUCKET_MATERIALES),
                startsWith("40/"), any(), eq("application/pdf"));
    }

    @Test
    @DisplayName("Un docente no sube material a un curso ajeno")
    void rechazaCursoAjeno() {
        prepararDocente();
        Curso ajeno = new Curso(docente, curso.getMateria(), seccion);
        ajeno.setId(41L);
        User otroUser = user("otto@educa360.pe", User.Role.DOCENTE, "Otto", "20000002");
        Docente otro = new Docente(otroUser, "Lic.");
        otro.setId(2L);
        when(userRepository.findByEmail("otto@educa360.pe")).thenReturn(Optional.of(otroUser));
        when(docenteRepository.findByUser(otroUser)).thenReturn(Optional.of(otro));
        when(cursoRepository.findById(41L)).thenReturn(Optional.of(ajeno));

        assertThrows(NotFoundException.class, () -> servicio.crear(
                "otto@educa360.pe", 41L, "Guía", null, null,
                new MockMultipartFile("archivo", "guia.pdf", "application/pdf", "x".getBytes())));
        verify(storageService, never()).upload(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Publicar fija fecha y despublicar la limpia")
    void publicaYDespublica() {
        prepararDocente();
        MaterialClase material = material(100L, false);
        when(materialRepository.findById(100L)).thenReturn(Optional.of(material));
        when(materialRepository.save(any(MaterialClase.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MaterialResponse publicado = servicio.cambiarPublicacion(
                "dana@educa360.pe", 100L, true);
        assertTrue(publicado.publicado());
        assertNotNull(publicado.fechaPublicacion());

        MaterialResponse despublicado = servicio.cambiarPublicacion(
                "dana@educa360.pe", 100L, false);
        assertFalse(despublicado.publicado());
        assertEquals(null, despublicado.fechaPublicacion());
    }

    @Test
    @DisplayName("La baja lógica desactiva el material y elimina el objeto")
    void eliminaMaterial() {
        prepararDocente();
        MaterialClase material = material(100L, true);
        when(materialRepository.findById(100L)).thenReturn(Optional.of(material));

        servicio.eliminar("dana@educa360.pe", 100L);

        assertFalse(material.isActivo());
        assertFalse(material.isPublicado());
        verify(storageService).delete(StorageService.BUCKET_MATERIALES,
                material.getStoragePath());
    }

    @Test
    @DisplayName("El alumno sólo ve materiales publicados de unidades publicadas")
    void filtraVisibilidadDelAlumno() {
        MaterialClase visible = material(100L, true);
        visible.setContenido(contenido);
        unidad.setEstado(Unidad.Estado.PUBLICADA);

        MaterialClase borrador = material(101L, false);
        borrador.setContenido(contenido);

        MaterialClase unidadBorrador = material(102L, true);
        Unidad otraUnidad = new Unidad(curso, unidad.getBimestre(), 2, "Otra");
        otraUnidad.setId(81L);
        otraUnidad.setEstado(Unidad.Estado.BORRADOR);
        Contenido otroContenido = new Contenido(otraUnidad, 1, "Tema");
        otroContenido.setId(91L);
        unidadBorrador.setContenido(otroContenido);

        when(userRepository.findByEmail("alma@educa360.pe")).thenReturn(Optional.of(userEstudiante));
        when(estudianteRepository.findByUser(userEstudiante)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(60L)).thenReturn(Optional.of(matricula));
        when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
        when(materialRepository.findByCursoIdAndPublicadoTrueAndActivoTrueOrderByCreatedAtDesc(40L))
                .thenReturn(List.of(visible, borrador, unidadBorrador));

        List<MaterialResponse> respuesta = servicio.listarDelEstudiante(
                "alma@educa360.pe", 40L);

        assertEquals(1, respuesta.size());
        assertEquals(100L, respuesta.get(0).id());
    }

    @Test
    @DisplayName("ADMIN descarga también materiales no publicados")
    void adminDescargaBorrador() {
        User admin = user("admin@educa360.pe", User.Role.ADMIN, "Ana", "10000001");
        MaterialClase material = material(100L, true);
        when(userRepository.findByEmail("admin@educa360.pe")).thenReturn(Optional.of(admin));
        when(materialRepository.findById(100L)).thenReturn(Optional.of(material));
        when(storageService.signedUrl(eq(StorageService.BUCKET_MATERIALES),
                eq(material.getStoragePath()), any())).thenReturn("https://firmada");

        ArchivoDescargaResponse respuesta = servicio.descargar(
                "admin@educa360.pe", 100L);

        assertEquals("https://firmada", respuesta.url());
        assertNotNull(respuesta.expiresAt());
    }

    @Test
    @DisplayName("El alumno no descarga un material no publicado")
    void alumnoNoDescargaBorrador() {
        MaterialClase material = material(100L, false);
        when(userRepository.findByEmail("alma@educa360.pe")).thenReturn(Optional.of(userEstudiante));
        when(materialRepository.findById(100L)).thenReturn(Optional.of(material));
        when(estudianteRepository.findByUser(userEstudiante)).thenReturn(Optional.of(estudiante));
        when(matriculaService.vigenteDe(60L)).thenReturn(Optional.of(matricula));

        assertThrows(NotFoundException.class, () -> servicio.descargar(
                "alma@educa360.pe", 100L));
    }

    @Test
    @DisplayName("Editar no permite mover el material a un contenido de otro curso")
    void rechazaContenidoDeOtroCurso() {
        prepararDocente();
        MaterialClase material = material(100L, true);
        when(materialRepository.findById(100L)).thenReturn(Optional.of(material));
        Curso otro = new Curso(docente, curso.getMateria(), seccion);
        otro.setId(41L);
        Unidad otraUnidad = new Unidad(otro, unidad.getBimestre(), 1, "Otra");
        otraUnidad.setId(81L);
        Contenido otroContenido = new Contenido(otraUnidad, 1, "Tema");
        otroContenido.setId(91L);
        when(contenidoRepository.findById(91L)).thenReturn(Optional.of(otroContenido));

        assertThrows(NotFoundException.class, () -> servicio.actualizar(
                "dana@educa360.pe", 100L,
                new MaterialUpdateRequest("Guía", null, 91L)));
    }

    // ==================== Helpers ====================

    private void prepararDocente() {
        lenient().when(userRepository.findByEmail("dana@educa360.pe"))
                .thenReturn(Optional.of(userDocente));
        lenient().when(docenteRepository.findByUser(userDocente))
                .thenReturn(Optional.of(docente));
        lenient().when(cursoRepository.findById(40L)).thenReturn(Optional.of(curso));
    }

    private MaterialClase material(Long id, boolean publicado) {
        MaterialClase material = new MaterialClase(curso, docente, null,
                "Guía", "40/uuid-guia.pdf", "guia.pdf", "application/pdf", 8L);
        material.setId(id);
        material.setPublicado(publicado);
        return material;
    }

    private User user(String email, User.Role rol, String nombres, String dni) {
        User user = new User(email, "hash", nombres, "Luna", dni, rol);
        user.setId((long) (email.hashCode() & 0x7fffffff));
        return user;
    }
}
