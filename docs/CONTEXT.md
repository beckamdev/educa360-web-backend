# Contexto del Proyecto: Educa360 (Backend)

> **Documento vivo — v3 (simplificado)**
> Última actualización: 2026-11-04
> Este archivo es la **fuente de verdad** del dominio, las reglas de negocio y el roadmap.
> Si el código y este documento difieren, se corrigen ambos en el mismo cambio.

---

## 0. Cómo leer este documento

| Sección | Contenido |
|---|---|
| §1 | Visión general y alcance |
| §2 | Stack, arquitectura y convenciones |
| §3 | Modelo de dominio |
| §4 | Roles, permisos y reglas de acceso |
| §5 | Módulos, endpoints y criterios de aceptación |
| §6 | Flujos principales |
| §7 | Base de datos y migraciones |
| §8 | Convenciones de API |
| §9 | Testing y CI |
| §10 | Operación y entorno |
| §11 | Estado actual y roadmap |
| §12 | Decisiones, supuestos y limitaciones |
| §13 | Registro de cambios |

### 0.1. Glosario

| Término | Significado en Educa360 |
|---|---|
| **Año escolar** | Período anual completo (ej. 2026). Ancla secciones, cursos, horarios y matrículas. Reemplaza al antiguo `PeriodoAcademico`, que representaba un bimestre. |
| **Bimestre** | Etapa de evaluación dentro del año (I–IV). Ventana donde viven unidades, notas y asistencia. |
| **Materia** | Catálogo de lo que se enseña (Matemática, Física, etc.). No depende del año. |
| **Sección** | Grupo de alumnos de un año (3°A). Dura todo el año. |
| **Curso** | Instancia de una materia en una sección, con un docente (Matemática · 3°A · profe X). Cuelgan horario, unidades, materiales, asistencias y notas. |
| **Matrícula** | Registro anual del alumno en una sección. Una por alumno y año. |
| **Unidad** | Planificación de un curso dentro de un bimestre. Agrupa contenidos y calificaciones. |
| **Contenido** | Tema o subtema planificado dentro de una unidad. No es una clase dictada. |
| **Asistencia** | Registro de un alumno en un bloque de horario y una fecha. |
| **Calificación** | Nota vigesimal (0–20) de un alumno en un criterio de una unidad. |
| **Ticket** | Solicitud de justificación de inasistencia creada por el apoderado y resuelta por Secretaría. |
| **Material** | Archivo o enlace publicado por el docente para un curso, opcionalmente ligado a un contenido. |
| **Comunicado** | Mensaje del docente a su sección, de Secretaría a toda la institución o del ADMIN a toda la institución o a una sección. |

### 0.2. Estados de madurez

- ✅ **Implementado:** existe en código y cumple lo aquí descrito.
- 🔧 **Refactor:** existe, pero cambia de semántica o de estructura.
- 🆕 **Pendiente:** diseñado aquí, aún no implementado.
- 📋 **Diferido:** reconocido, fuera del alcance de la v3.

---

## 1. Visión general

- **Nombre:** Educa360.
- **Institución objetivo:** Colegio Nuestra Señora de Monserrat (Nivel Secundaria).
- **Propósito:** gestión académica, seguimiento estudiantil y comunicación entre institución, docentes, estudiantes y apoderados.
- **Tipo de aplicación:** API RESTful desacoplada para un SPA (React + Vite).
- **Alcance académico:** secundaria, grados 1 a 5.
- **Idioma del dominio:** español. Las clases técnicas pueden usar inglés (`Service`, `Repository`, `Controller`), pero las entidades y reglas se nombran en español.

### 1.1. Fuera de alcance de la v3

- Pensiones, cuotas, pasarela de pagos o cobranza.
- Reprogramación de clases: no se modelan sesiones reales ni clases movidas de fecha.
- Cambios de sección después de iniciado el año: la matrícula queda fijada.
- Aplicación móvil nativa.
- Primaria o inicial.
- Firma digital de actas.
- Notificaciones push o correo transaccional.
- Integración con sistemas externos del Ministerio.

### 1.2. Principios de diseño

1. **Una sola fuente de verdad por concepto.** Si algo se puede derivar, no se duplica sin razón.
2. **La identidad sale del token.** Los endpoints de estudiante, apoderado y docente no reciben ids de identidad desde el cliente para decidir qué datos devolver.
3. **El rol no basta.** Además del rol, se valida la pertenencia al recurso (ownership).
4. **Lo histórico no se borra.** Personas, matrículas, notas y asistencias se corrigen o se desactivan; no se eliminan físicamente.
5. **La configuración del año sí se puede borrar** si no tiene historia.
6. **Toda regla de negocio vive en el servicio.** La BD refuerza lo que pueda reforzar.
7. **Los DTO son records** y no se exponen entidades JPA.

---

## 2. Stack, arquitectura y convenciones

### 2.1. Stack

| Pieza | Tecnología |
|---|---|
| Lenguaje | Java 25 |
| Framework | Spring Boot 4.1.1 (WebMVC, Security, Data JPA) |
| Autenticación | JWT stateless + RBAC |
| Base de datos | PostgreSQL 17 |
| Migraciones | Flyway **solo en la fase final**; durante el desarrollo se recrea la BD |
| Almacenamiento | Supabase Storage (buckets privados) |
| Documentación | springdoc-openapi / Swagger UI |
| Dependencias | Maven |
| Pruebas | JUnit 5 + MockMvc + Testcontainers (PostgreSQL 17) |
| Contenedores | Docker Compose |

### 2.2. Arquitectura en capas

```text
controller  →  service  →  repository  →  entity
     ↑             ↑
     └── dto ──────┘
```

- `controller`: endpoints REST, validación con `@Valid`, autorización con `@PreAuthorize`.
- `service`: lógica de negocio transaccional (`@Transactional`), reglas, ownership.
- `repository`: interfaces Spring Data JPA.
- `entity`: modelos JPA. Todas las entidades extienden `Auditable`.
- `dto`: records de request/response.
- `security`: `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `AppUserDetailsService`.
- `exception`: `GlobalExceptionHandler`, `ApiError`, `ConflictException`, `RateLimitedException`.

### 2.3. Convenciones

- Paquete base: `com.educa360.backend`.
- Entidades y campos de dominio en español (`Estudiante`, `Matricula`, `fechaNacimiento`).
- Clases técnicas en inglés (`EstudianteService`, `EstudianteRepository`, `EstudianteController`).
- DTOs: `XxxRequest`, `XxxResponse`, `XxxResumenResponse`.
- Fechas: `LocalDate` para fechas sin hora; `LocalDateTime` para marcas de tiempo.
- Notas: `BigDecimal` con escala 2.
- Enums persistidos como `STRING`.
- `@Transactional` en servicios que escriben más de una tabla.
- `flush()` explícito cuando una constraint unique pueda ver filas viejas antes de borrarlas.
- Getters/setters explícitos y `toString` seguro (solo ids).

### 2.4. Auditoría

`Auditable` provee:

| Campo | Tipo | Regla |
|---|---|---|
| `id` | Long | IDENTITY |
| `createdAt` | LocalDateTime | automático al crear |
| `updatedAt` | LocalDateTime | automático al actualizar |

Entidades con responsabilidad explícita:
- `Asistencia.registradoPor` (Docente).
- `Calificacion.registradoPor` (Docente).
- `TicketJustificacion.resueltoPor` (Secretaria).
- `MaterialClase.docente` (quien publica).
- `Comunicado.autor` (User).

### 2.5. Errores uniformes

Todas las respuestas de error usan `ApiError`:

```json
{
  "timestamp": "2026-11-02T10:15:30",
  "status": 409,
  "error": "Conflict",
  "message": "Ya existe una matrícula para este estudiante en el año 2026",
  "path": "/api/v1/secretaria/matriculas",
  "details": []
}
```

Códigos:
- `400`: validación de DTO, cuerpo ilegible, regla de negocio inválida.
- `401`: credenciales inválidas o token ausente/expirado.
- `403`: usuario inactivo o sin permiso.
- `404`: recurso inexistente.
- `405`: método no permitido.
- `409`: conflicto de unicidad, estado o regla de negocio.
- `415`: `Content-Type` incorrecto.
- `429`: límite de intentos de login.
- `500`: fallo inesperado, sin stack trace.

---

## 3. Modelo de dominio

### 3.1. Jerarquía académica

```text
AnioEscolar (ej. 2026)
 ├── Bimestre (I..IV)
 ├── Seccion (3°A) ── Matricula (alumno en el año)
 │     └── Curso (materia + docente) ── HorarioBloque ── Asistencia
 │           └── Unidad (curso × bimestre)
 │                 ├── Contenido ── MaterialClase
 │                 └── Calificacion
 └── TicketJustificacion (por alumno y fecha)
```

Reglas estructurales:

1. `AnioEscolar` es el ancla anual. Secciones, cursos, horarios y matrículas se crean una vez por año.
2. `Bimestre` divide el año en ventanas de evaluación.
3. `Seccion` pertenece a un año. `Curso` pertenece a una sección; por transitividad, a un año.
4. `Matricula` es anual y une alumno + sección + año.
5. Asistencia y calificaciones cuelgan de `Matricula`, no del estudiante suelto.
6. `HorarioBloque` es la plantilla semanal. No hay entidad de sesión real: la asistencia se registra por bloque y fecha.
7. `Unidad` y `Calificacion` dan estructura a las notas; `Contenido` y `MaterialClase` a los recursos.

### 3.2. Entidades

#### 3.2.1. Identidad y personas

##### `User` ✅ — tabla `users`

| Campo | Tipo | Regla |
|---|---|---|
| `email` | String | único, obligatorio, minúsculas, formato email |
| `password` | String | obligatorio, BCrypt, nunca se expone |
| `nombres` | String | obligatorio |
| `apellidos` | String | obligatorio |
| `dni` | String | único, obligatorio, 8 dígitos |
| `rol` | enum `Role` | `ADMIN`, `SECRETARIA`, `DOCENTE`, `ESTUDIANTE`, `APODERADO` |
| `activo` | boolean | por defecto `true`; única baja lógica de la cuenta |

Relaciones:
- **1—N** `Telefono` (cascade ALL, orphanRemoval).
- **1—0..1** con cada perfil (`Docente`, `Estudiante`, `Apoderado`, `Secretaria`).
- `ADMIN` no tiene tabla de perfil.

Reglas:
- El rol es inmutable.
- No se elimina físicamente. `activo=false` corta el acceso.
- Email y DNI se validan en `UsuarioService.crearCuenta()`.

##### `Telefono` ✅ — tabla `telefonos`

| Campo | Tipo | Regla |
|---|---|---|
| `numero` | String | obligatorio |
| `user_id` | FK → `User` | obligatorio |

Único `(user_id, numero)`. Regla: `ESTUDIANTE` no registra teléfonos propios.

##### `Docente` ✅ — tabla `docentes`

| Campo | Tipo | Regla |
|---|---|---|
| `user_id` | FK → `User` | única, obligatoria, 1—1 |
| `titulo` | String | opcional |

Relaciones:
- **N—N** `Materia` por `docente_materia` (único `(docente_id, materia_id)`).
- **1—N** `Curso`.
- **1—N** `Seccion` como tutor (opcional).

Regla: no se desactiva la cuenta de un docente con cursos en el año `ABIERTO` sin reasignar o cerrar esos cursos.

##### `Estudiante` ✅ — tabla `estudiantes`

| Campo | Tipo | Regla |
|---|---|---|
| `user_id` | FK → `User` | única, obligatoria, 1—1 |
| `fechaNacimiento` | LocalDate | obligatoria |
| `estado` | enum | `ACTIVO`, `TRASLADADO`, `RETIRADO`, `EGRESADO` |

Relaciones:
- **1—N** `Matricula`.
- **N—N** `Apoderado` por `ApoderadoEstudiante`.
- **1—N** `TicketJustificacion`.

Reglas:
- `estado` es el ciclo de vida institucional, distinto de `Matricula.situacionFinal`.
- `estaHabilitado()` = `estado == ACTIVO`.
- `EGRESADO` es terminal.
- `TRASLADADO` o `RETIRADO` pueden volver a `ACTIVO` por acción de `SECRETARIA` si el alumno reingresa; se reactiva la matrícula del año si sigue abierta o se crea una nueva si cambió de año.
- Retirar académicamente no desactiva la cuenta automáticamente: `User.activo` es una baja de acceso separada.

##### `Apoderado` ✅ — tabla `apoderados`

| Campo | Tipo | Regla |
|---|---|---|
| `user_id` | FK → `User` | única, obligatoria, 1—1 |

Relaciones:
- **1—N** `ApoderadoEstudiante` (cascade ALL).
- **1—N** `TicketJustificacion`.

##### `Secretaria` ✅ — tabla `secretarias`

| Campo | Tipo | Regla |
|---|---|---|
| `user_id` | FK → `User` | única, obligatoria, 1—1 |

Relación: **1—N** `TicketJustificacion` como resolutor.

##### `ApoderadoEstudiante` ✅ — tabla `apoderado_estudiante`

| Campo | Tipo | Regla |
|---|---|---|
| `apoderado_id` | FK → `Apoderado` | obligatoria |
| `estudiante_id` | FK → `Estudiante` | obligatoria |
| `parentesco` | enum | `PADRE`, `MADRE`, `TUTOR`, `HERMANO`, `OTRO` |
| `responsablePrincipal` | boolean | obligatorio |
| `activo` | boolean | por defecto `true` |

Único `(apoderado_id, estudiante_id)`. Solo un responsable principal activo por estudiante (índice único parcial).

#### 3.2.2. Estructura anual

##### `AnioEscolar` ✅ — tabla `anios_escolares`

> Reemplaza al antiguo `PeriodoAcademico`. El nombre anterior queda como alias histórico y no debe usarse en código nuevo.

| Campo | Tipo | Regla |
|---|---|---|
| `anio` | Integer | único (2026) |
| `nombre` | String | único; ej. "Año Escolar 2026" |
| `fechaInicio` | LocalDate | obligatoria; primer día de clases; debe pertenecer al año calendario |
| `estado` | enum | `PLANIFICADO`, `ABIERTO`, `CERRADO` |

Relaciones: **1—N** `Bimestre`, `Seccion`, `Matricula`.

Reglas:
- Solo un año `ABIERTO` en todo el sistema.
- `PLANIFICADO`: año futuro editable; se puede preparar estructura, pero no registrar asistencia ni notas.
- `ABIERTO`: año vigente; permite matrícula, asistencia y calificaciones.
- `CERRADO`: histórico; solo lectura.
- El número de año y el nombre son únicos; `fechaInicio` debe caer en el año calendario (`fechaInicio.getYear() == anio`).
- El cierre del año lo definen sus bimestres: el último bimestre marca el último día de clases.
- No se cierra con bimestres `ABIERTO`.
- No se elimina si tiene secciones o matrículas.
- `fechaInicio` marca el primer día de clases y es el corte para cambios de sección: antes de esa fecha se puede cambiar; desde esa fecha, no.

##### `Bimestre` ✅ — tabla `bimestres`

| Campo | Tipo | Regla |
|---|---|---|
| `anio_id` | FK → `AnioEscolar` | obligatoria |
| `numero` | Integer | 1..4 |
| `nombre` | String | derivado de `numero` ("Bimestre I") |
| `fechaInicio` | LocalDate | obligatoria; no antes de `AnioEscolar.fechaInicio` |
| `fechaFin` | LocalDate | obligatoria; posterior a inicio y en el mismo año calendario |
| `estado` | enum | `PLANIFICADO`, `ABIERTO`, `CERRADO` |

Único `(anio_id, numero)`. Sin solape dentro del año.
Reglas:
- Solo un bimestre `ABIERTO` en todo el sistema.
- Se abre solo si el año está `ABIERTO`.
- `PLANIFICADO` es editable; `CERRADO` bloquea nuevas notas y asistencias.
- Reabrir es una acción explícita de `ADMIN`, auditada.
- No se elimina si tiene unidades.

##### `Materia` ✅ — tabla `materias`

| Campo | Tipo | Regla |
|---|---|---|
| `nombre` | String | único, obligatorio |
| `descripcion` | String | opcional |
| `activa` | boolean | por defecto `true` |

Relaciones: **N—N** `Docente`; **1—N** `Curso`.
Regla: `activa=false` impide nuevas asignaciones y cursos; los existentes se conservan.

##### `Seccion` ✅ — tabla `secciones`

| Campo | Tipo | Regla |
|---|---|---|
| `anio_id` | FK → `AnioEscolar` | obligatoria |
| `grado` | Integer | 1..5 |
| `letra` | String | una mayúscula `[A-Z]` |
| `aula` | String | texto libre, opcional |
| `tutor_id` | FK → `Docente` | opcional |

Único `(anio_id, grado, letra)`.
Reglas: no se mueve de año; no se elimina si tiene cursos o matrículas.

##### `Curso` ✅ — tabla `cursos`

| Campo | Tipo | Regla |
|---|---|---|
| `docente_id` | FK → `Docente` | obligatoria |
| `materia_id` | FK → `Materia` | obligatoria |
| `seccion_id` | FK → `Seccion` | obligatoria |

Único `(materia_id, seccion_id)`.
Reglas:
- Materia activa y habilitada al docente.
- Un solo docente por curso.
- No se elimina si tiene unidades, asistencias, calificaciones o materiales.

##### `HorarioBloque` ✅ — tabla `horario_bloques`

| Campo | Tipo | Regla |
|---|---|---|
| `curso_id` | FK → `Curso` | obligatoria |
| `dia` | enum `DiaSemana` | `LUNES`..`SABADO` |
| `horaInicio` | LocalTime | obligatoria |
| `horaFin` | LocalTime | obligatoria, posterior a inicio |

Único `(curso_id, dia, hora_inicio)`. Intervalos `[inicio, fin)`.
Reglas:
- Sin solapes con otro curso de la misma sección el mismo día.
- Sin solapes con otro curso del mismo docente el mismo día.
- Es una plantilla semanal; no hay sesiones reales ni reprogramaciones.
- `PUT` reemplaza la hoja completa solo si el curso no tiene asistencias. Si ya tiene, `409`.
- La asistencia se registra por `(bloque, fecha)` y exige que la fecha caiga en el día del bloque.

#### 3.2.3. Matrícula

##### `Matricula` ✅ — tabla `matriculas`

| Campo | Tipo | Regla |
|---|---|---|
| `estudiante_id` | FK → `Estudiante` | obligatoria |
| `anio_id` | FK → `AnioEscolar` | obligatoria |
| `seccion_id` | FK → `Seccion` | obligatoria |
| `fechaMatricula` | LocalDate | obligatoria |
| `situacionFinal` | enum | `EN_CURSO`, `APROBADO`, `DESAPROBADO`, `RETIRADO` |
| `observacion` | String | opcional |

Único `(estudiante_id, anio_id)`.
Relaciones: **N—1** `Estudiante`, `AnioEscolar`, `Seccion`; **1—N** `Asistencia`, `Calificacion`.

Reglas:
- Es el registro académico anual del alumno. El nombre `Matricula` no representa una cuota ni un pago: en este sistema no hay módulo de pagos, pensiones ni cuotas.
- Solo alumnos `ACTIVO` pueden matricularse.
- La sección debe pertenecer al mismo año.
- Nace `EN_CURSO`.
- `RETIRADO` se asigna al retirar al alumno; `APROBADO`/`DESAPROBADO` al cerrar el año.
- No se elimina; se corrige o se cambia de estado.
- **Limitación:** no hay traslados una vez iniciado el año. La sección puede modificarse solo antes de `AnioEscolar.fechaInicio` (primer día de clases) y mientras no exista ninguna asistencia ni calificación. Al iniciar clases, la matrícula queda fijada.

#### 3.2.4. Planificación docente

##### `Unidad` ✅ — tabla `unidades`

| Campo | Tipo | Regla |
|---|---|---|
| `curso_id` | FK → `Curso` | obligatoria |
| `bimestre_id` | FK → `Bimestre` | obligatoria; mismo año que la sección del curso |
| `numero` | Integer | orden dentro del curso y bimestre |
| `titulo` | String | obligatorio |
| `descripcion` | String (text) | opcional |
| `fechaInicio` | LocalDate | opcional, dentro del bimestre |
| `fechaFin` | LocalDate | opcional, dentro del bimestre |
| `estado` | enum | `BORRADOR`, `PUBLICADA` |

Único `(curso_id, bimestre_id, numero)`.
Reglas:
- Solo el docente del curso crea/edita sus unidades.
- El `numero` lo asigna el sistema: siguiente correlativo dentro del curso y bimestre.
- Se puede planificar en un bimestre `PLANIFICADO` o `ABIERTO`; no en `CERRADO`.
- El estudiante y el apoderado solo ven unidades `PUBLICADA`.
- La respuesta de una unidad incluye sus contenidos ordenados.
- Se puede publicar una unidad sin contenidos; los contenidos que se agreguen después se ven al publicarse.
- El `PUT` edita título, descripción y fechas; no mueve la unidad a otro bimestre.
- No se elimina si tiene calificaciones o contenidos con materiales.
- Se puede despublicar solo si no tiene calificaciones.

##### `Contenido` ✅ — tabla `contenidos`

| Campo | Tipo | Regla |
|---|---|---|
| `unidad_id` | FK → `Unidad` | obligatoria |
| `orden` | Integer | posición dentro de la unidad |
| `titulo` | String | obligatorio |
| `descripcion` | String (text) | opcional |
| `fechaPrevista` | LocalDate | opcional |

Único `(unidad_id, orden)`.
Reglas:
- Es un tema planificado, no una clase dictada.
- El `orden` lo asigna el sistema al final de la unidad; el alta no lo recibe.
- Reordenar se hace con un endpoint bulk en dos pasadas dentro de la misma transacción (valores temporales y luego definitivos).
- No se elimina si tiene materiales; se desvinculan o se bloquea la baja.

#### 3.2.5. Asistencia

##### `Asistencia` ✅ — tabla `asistencias`

| Campo | Tipo | Regla |
|---|---|---|
| `matricula_id` | FK → `Matricula` | obligatoria |
| `bloque_id` | FK → `HorarioBloque` | obligatoria |
| `fecha` | LocalDate | obligatoria; no futura |
| `estado` | enum | `PRESENTE`, `TARDANZA`, `FALTA`, `JUSTIFICADA` |
| `observacion` | String | opcional |
| `ticket_id` | FK → `TicketJustificacion` | nullable; se llena al aprobar el ticket o al registrar una falta ya justificada |
| `registradoPor_id` | FK → `Docente` | opcional |

Único `(matricula_id, bloque_id, fecha)`.
Reglas:
- El bloque debe pertenecer a un curso de la sección de la matrícula.
- La fecha debe caer en el día del bloque y dentro de un bimestre `ABIERTO`.
- No se registra asistencia de una fecha futura.
- Solo el docente del curso registra asistencia.
- El docente envía `asistio` (verdadero/falso), no el estado: el sistema deriva `PRESENTE` o `TARDANZA` según la hora del servidor. Se considera `TARDANZA` después de 15 minutos desde `horaInicio` del bloque.
- Para fechas pasadas se asume `PRESENTE`: no hay forma de reconstruir la hora de llegada.
- `JUSTIFICADA` no la marca el docente: nace en M7 cuando Secretaría aprueba un ticket. En M5 el enum existe, pero el estado no es alcanzable desde la API.
- El registro es un upsert parcial por bloque y fecha: solo viajan los alumnos que se registran o corrigen.
- La consulta de la clase devuelve a todos los alumnos activos de la sección; los no registrados llegan con estado `null`.
- El historial del alumno incluye total, efectivas, faltas y porcentaje.
- No se elimina; se corrige dejando auditoría.
- **Limitación:** no hay reprogramaciones. Si una clase se mueve de fecha, el sistema no la representa; se registra solo en el día del bloque.

#### 3.2.6. Calificaciones

##### `Calificacion` ✅ — tabla `calificaciones`

| Campo | Tipo | Regla |
|---|---|---|
| `matricula_id` | FK → `Matricula` | obligatoria |
| `unidad_id` | FK → `Unidad` | obligatoria |
| `criterio` | String(120) | obligatorio; se guarda con `trim`; ej. "Examen parcial", "Práctica 1" |
| `nota` | BigDecimal(4,2) | `0.00` a `20.00`; máximo dos decimales |
| `registradoPor_id` | FK → `Docente` | opcional |

Único `(matricula_id, unidad_id, criterio)` y `check (nota >= 0 and nota <= 20)`.
Reglas:
- La sección de la matrícula debe ser la sección del curso de la unidad.
- Solo el docente dueño del curso califica; una unidad o curso ajeno responde `404`.
- El bimestre de la unidad debe estar `ABIERTO` y el año no `CERRADO`.
- Escala vigesimal estricta, dos decimales; más de dos decimales responde `400`.
- El alta y la corrección son un upsert parcial por `(matrícula, criterio)`.
- El alumno y el apoderado sólo ven notas de unidades `PUBLICADA`; docente, secretaría y admin ven también los borradores.
- La baja es física y sólo se permite con el bimestre `ABIERTO`.
- `createdAt` de `Auditable` es la fecha de registro; no se duplica en un campo aparte.

##### Cálculo de promedios

- **Promedio de unidad:** promedio simple de sus calificaciones.
- **Promedio de bimestre (por curso):** promedio simple de las unidades con notas.
- **Promedio anual (por curso):** promedio simple de los bimestres con notas.
- **Promedio general del alumno:** promedio simple de los promedios anuales de todos sus cursos. Se usa para reportes y boleta, no para decidir la aprobación.
- **Sin notas:** una unidad, bimestre o curso sin notas no entra en el promedio; si no hay ninguna nota, el promedio es `null`.
- **Semáforo (por curso y general):** 🟢 `≥ 16.00`; 🟡 `11.00 – 15.99`; 🔴 `< 11.00`. Es solo presentación: lo calcula el frontend a partir del promedio; la aprobación sigue siendo por curso.
- **Aprobación de un curso:** promedio anual del curso ≥ `11.00`.
- **Aprobación del grado:** todos los cursos deben estar aprobados y la asistencia global del alumno debe ser ≥ `70%`.
- **Redondeo:** `HALF_UP` a 2 decimales.
- **Nota aprobatoria:** `11.00`.
- **Asistencia mínima para aprobar:** `70%`.
- **Asistencia efectiva:** `PRESENTE + TARDANZA + JUSTIFICADA`. Solo `FALTA` cuenta como inasistencia injustificada. El porcentaje se calcula sobre asistencias registradas.
- Si falta la información de notas de algún curso, el cierre se bloquea y requiere revisión de `ADMIN`.
- Los promedios se calculan por consulta; no se guardan snapshots.

#### 3.2.7. Justificaciones

##### `TicketJustificacion` ✅ — tabla `tickets_justificacion`

| Campo | Tipo | Regla |
|---|---|---|
| `apoderado_id` | FK → `Apoderado` | obligatoria; vínculo activo con el estudiante |
| `estudiante_id` | FK → `Estudiante` | obligatoria |
| `fechaInasistencia` | LocalDate | obligatoria; no futura |
| `bloque_id` | FK → `HorarioBloque` | opcional; si es `NULL`, aplica a todo el día |
| `motivo` | String(500) | obligatorio; se guarda con `trim` |
| `storagePath` | String(500) | opcional; ruta privada del archivo en el bucket `sustentos` (M8). Nunca se expone como URL pública. |
| `estado` | enum | `PENDIENTE`, `APROBADO`, `RECHAZADO` |
| `fechaResolucion` | LocalDateTime | la pone el servidor al resolver |
| `resueltoPor_id` | FK → `Secretaria` | opcional hasta resolver |
| `observacionesAdmin` | String(500) | opcional; se guarda con `trim` |

Reglas:
- Solo el apoderado con vínculo activo crea tickets para su representado.
- La fecha no puede ser futura.
- Si se indica `bloque_id`, debe ser de un curso de la sección de la matrícula del alumno en el año de la fecha, y la fecha debe caer en el día de ese bloque.
- Sin `bloque_id`, el ticket cubre todo el día.
- Solo se resuelve desde `PENDIENTE`; no hay vuelta atrás.
- Al aprobar: las asistencias `FALTA` del alumno en esa fecha (y bloque, si se indicó) pasan a `JUSTIFICADA` y se vinculan al ticket, en la misma transacción. `PRESENTE` y `TARDANZA` no se tocan.
- Si la asistencia se registra después, el servicio la crea directamente como `JUSTIFICADA` y la vincula al ticket aprobado.
- No se permite más de un ticket `PENDIENTE` o `APROBADO` para el mismo alumno, fecha y bloque.
- Tampoco se permite un ticket de día completo y otro de bloque para la misma fecha del mismo alumno: el alcance más amplio prevalece.
- Un ticket `RECHAZADO` ya no bloquea uno nuevo.
- El sustento es opcional. Se sube como archivo al bucket privado `sustentos` y solo se puede adjuntar/reemplazar mientras el ticket está `PENDIENTE`. Se descarga con URL firmada de 5 minutos.
- No se elimina; los estados son la historia.

#### 3.2.8. Materiales y comunicados

##### `MaterialClase` ✅ — tabla `materiales_clase`

| Campo | Tipo | Regla |
|---|---|---|
| `curso_id` | FK → `Curso` | obligatoria |
| `docente_id` | FK → `Docente` | quien publica; debe ser el docente del curso |
| `contenido_id` | FK → `Contenido` | opcional; si existe, debe ser del mismo curso |
| `titulo` | String | obligatorio |
| `descripcion` | String | opcional |
| `storagePath` | String | ruta en Supabase Storage |
| `nombreOriginal` | String | nombre del archivo |
| `mimeType` | String | tipo MIME |
| `tamanoBytes` | Long | tamaño |
| `publicado` | boolean | visible para alumnos; al crear queda en `false` |
| `fechaPublicacion` | LocalDateTime | automática al publicar; se limpia al despublicar |
| `activo` | boolean | baja lógica; al desactivar deja de ser accesible |

Reglas:
- Solo el docente del curso sube materiales.
- Al crear, el material queda no publicado; se publica con un `PATCH` explícito.
- Un material no publicado solo lo ve el docente y `ADMIN`.
- Si está ligado a un contenido de unidad `BORRADOR`, no se muestra al alumno aunque `publicado=true`.
- La baja es lógica (`activo=false`, `publicado=false`) y el objeto se elimina del bucket en la misma operación; si Storage falla, el material queda inactivo y no accesible.

##### `Comunicado` ✅ — tabla `comunicados`

| Campo | Tipo | Regla |
|---|---|---|
| `autor_id` | FK → `User` | obligatoria; quién escribió el mensaje |
| `anio_id` | FK → `AnioEscolar` | obligatoria |
| `contenido` | String (text) | obligatorio; el mensaje |
| `seccion_id` | FK → `Seccion` | opcional; `NULL` = general para todo el año |
| `activo` | boolean | baja lógica |

Reglas:
- `createdAt` de `Auditable` es la fecha y hora del mensaje; no se duplica en otro campo.
- `ADMIN` publica mensajes generales (`seccion_id=NULL`) o para una sección, y edita/elimina cualquiera.
- `SECRETARIA` publica solo mensajes generales (`seccion_id=NULL`), para todos los grados, y gestiona solo los suyos.
- `DOCENTE` publica solo en secciones donde tiene cursos o tutoría, y gestiona solo los suyos.
- El alumno ve los generales y los de su sección.
- El apoderado ve los generales y los de la sección de cada hijo activo con matrícula vigente.
- No hay `tipo` ni `alcance` separados: el filtro es `seccion_id` (`NULL` = general).
- El contenido es obligatorio, se guarda con `trim` y tiene un máximo de 5000 caracteres.
- Un año `CERRADO` es solo lectura: crear, editar o dar de baja responde `409`.
- No se elimina; se desactiva (`activo=false`).

### 3.3. Restricciones e índices de base de datos

Las unicidades simples se declaran con JPA (`@Table(uniqueConstraints=...)`). Las unicidades parciales y los `CHECK` se validan en servicio durante el desarrollo y se materializan en Flyway al final (M11), porque JPA no expresa índices parciales.

| Tabla | Restricción |
|---|---|
| `users` | unique `email`, unique `dni` |
| `telefonos` | unique `(user_id, numero)` |
| `docentes` | unique `user_id` |
| `estudiantes` | unique `user_id` |
| `apoderados` | unique `user_id` |
| `secretarias` | unique `user_id` |
| `apoderado_estudiante` | unique `(apoderado_id, estudiante_id)` |
| `apoderado_estudiante` | unique parcial `(estudiante_id)` where `responsable_principal=true and activo=true` |
| `anios_escolares` | unique `anio`, unique `nombre` |
| `anios_escolares` | unique parcial `(estado)` where `estado='ABIERTO'` |
| `bimestres` | unique `(anio_id, numero)` |
| `bimestres` | unique parcial `(estado)` where `estado='ABIERTO'` |
| `secciones` | unique `(anio_id, grado, letra)` |
| `cursos` | unique `(materia_id, seccion_id)` |
| `horario_bloques` | unique `(curso_id, dia, hora_inicio)` |
| `matriculas` | unique `(estudiante_id, anio_id)` |
| `unidades` | unique `(curso_id, bimestre_id, numero)` |
| `contenidos` | unique `(unidad_id, orden)` |
| `asistencias` | unique `(matricula_id, bloque_id, fecha)` |
| `calificaciones` | unique `(matricula_id, unidad_id, criterio)` |
| `calificaciones` | check `nota >= 0 and nota <= 20` |
| `horario_bloques` | check `hora_fin > hora_inicio` |
| `tickets_justificacion` | unique parcial `(estudiante_id, fecha_inasistencia, coalesce(bloque_id, 0))` where `estado in ('PENDIENTE','APROBADO')` |

Reglas de integridad que se validan en servicio:
- La sección de `Matricula` pertenece al mismo año que `AnioEscolar`.
- El bloque de `Asistencia` pertenece a un curso de la sección de la matrícula.
- La fecha de `Asistencia` cae en el día del bloque y dentro de un bimestre `ABIERTO`.
- La unidad de `Calificacion` pertenece a un curso de la sección de la matrícula.
- El bimestre de la unidad está `ABIERTO` para registrar notas.
- El docente autenticado es el del curso.
- El apoderado autenticado tiene vínculo activo con el estudiante.
- El estudiante autenticado tiene matrícula en el año vigente.
- Tickets: solo un ticket activo (`PENDIENTE` o `APROBADO`) por alumno, fecha y bloque. Un ticket de día completo (`bloque_id=NULL`) bloquea cualquier ticket activo de esa fecha y viceversa; dos bloques distintos del mismo día sí coexisten. El índice parcial solo cubre el mismo bloque, así que el alcance día completo vs. bloque se valida en `JustificacionService`.

### 3.4. Estados y transiciones

#### `AnioEscolar`

```text
PLANIFICADO → ABIERTO → CERRADO
                  ↑         │
                  └─────────┘  (reapertura excepcional de ADMIN, auditada)
```

- Solo un `ABIERTO`.
- No se cierra con bimestres abiertos.
- `CERRADO` es histórico: no admite matrículas, asistencia ni notas nuevas.

#### `Bimestre`

```text
PLANIFICADO → ABIERTO → CERRADO
                  ↑         │
                  └─────────┘  (reapertura excepcional de ADMIN, auditada)
```

- Solo un `ABIERTO` en todo el sistema.
- Se abre solo si el año está `ABIERTO`.
- `PLANIFICADO` es editable; `CERRADO` bloquea notas y asistencia.

#### `Matricula`

```text
situacionFinal:  EN_CURSO → APROBADO
                     ├────→ DESAPROBADO
                     └────→ RETIRADO
                              │
                              └──→ EN_CURSO  (reingreso en el mismo año, acción de SECRETARIA)
```

#### `TicketJustificacion`

```text
PENDIENTE → APROBADO
PENDIENTE → RECHAZADO
```

- No hay vuelta atrás. Una corrección se hace con un nuevo ticket o una acción administrativa auditada.

#### `Unidad`

```text
BORRADOR → PUBLICADA
    ↑          │
    └──────────┘  (solo si no tiene calificaciones)
```

#### `Asistencia`

```text
PRESENTE / TARDANZA / FALTA / JUSTIFICADA
```

- `JUSTIFICADA` requiere `ticket_id` aprobado, salvo corrección administrativa auditada.

#### `MaterialClase`

```text
publicado=false ⇄ publicado=true
activo=true → activo=false  (terminal)
```

- Publicar fija `fechaPublicacion`; despublicar la limpia.
- Un material inactivo no vuelve a activarse ni es accesible.

#### `Estudiante`

```text
ACTIVO → TRASLADADO
   ├───→ RETIRADO
   └───→ EGRESADO

TRASLADADO → ACTIVO  (reingreso, acción de SECRETARIA)
RETIRADO   → ACTIVO  (reingreso, acción de SECRETARIA)
EGRESADO   → (terminal)
```

- `EGRESADO` se asigna al cerrar el último año aprobado.
- `TRASLADADO` y `RETIRADO` cierran la matrícula del año.
- El reingreso reactiva la matrícula si el año sigue abierto o crea una nueva si cambió de año.

### 3.5. Reglas de borrado y archivado

| Entidad | Política |
|---|---|
| `User` | Baja lógica (`activo=false`). Nunca borrado físico. |
| Perfiles (`Docente`, `Estudiante`, `Apoderado`, `Secretaria`) | No se borran; se desactiva la cuenta. |
| `Telefono` | Se puede quitar de la colección (orphanRemoval). |
| `ApoderadoEstudiante` | Baja lógica (`activo=false`); re-vincular reactiva. |
| `AnioEscolar` | No se borra si tiene secciones o matrículas. |
| `Bimestre` | No se borra si tiene unidades. |
| `Materia` | Archivado lógico (`activa=false`). |
| `Seccion` | No se borra si tiene cursos o matrículas. |
| `Curso` | Baja física solo si no tiene unidades, asistencias, calificaciones ni materiales. |
| `HorarioBloque` | No se borra si el curso tiene asistencias o si algún ticket lo referencia; el `PUT` de horario devuelve `409` en ese caso. |
| `Matricula` | No se borra; estados y correcciones. |
| `Unidad` | No se borra si tiene calificaciones o contenidos con materiales; si no, sus contenidos se eliminan en cascada. |
| `Contenido` | Se borra solo si no tiene materiales; si tiene, se desvinculan o se bloquea. |
| `Asistencia` | No se borra; se corrige con auditoría. |
| `Calificacion` | Baja física sólo con el bimestre `ABIERTO`; en `CERRADO` se corrige, no se borra. |
| `TicketJustificacion` | No se borra; estados. |
| `MaterialClase` | Baja lógica (`activo=false`) y eliminación del objeto en Storage en la misma operación. |
| `Comunicado` | Baja lógica (`activo=false`). |

### 3.6. Limitaciones de alcance

1. **No hay reprogramaciones:** la asistencia se registra en el día del bloque. Una clase movida de fecha no se representa.
2. **No hay cambios de sección una vez iniciado el año:** la matrícula queda fijada al llegar `AnioEscolar.fechaInicio`.
3. **No hay módulo de pagos:** no se modelan pensiones, cuotas ni pasarela. Secretaría solo registra matrícula y estados académicos.
4. **No hay actas inmutables:** los promedios se calculan por consulta; un snapshot de actas queda diferido.
5. **No hay entidad `Aula`:** el aula es texto libre en `Seccion`.
6. **No hay primaria ni inicial:** grados 1 a 5 de secundaria.
7. **No hay notificaciones push/correo:** los comunicados se consultan en la API.

---

## 4. Roles, permisos y reglas de acceso

### 4.1. Roles

1. **`ADMIN`** (Coordinación / Dirección)
   - Gestión de cuentas del personal.
   - Apertura y gestión de años, bimestres, materias, secciones, cursos y horarios.
   - Comunicados generales o para una sección.
   - Reportes y métricas globales.
   - Cierre de año y fijación de `situacionFinal`.
   - No resuelve tickets: esa responsabilidad es de `SECRETARIA`.

2. **`SECRETARIA`** (Personal administrativo)
   - Registro de estudiantes y apoderados.
   - Matrículas y retiros.
   - Resolución de tickets de justificación.
   - Consulta y actualización acotada de la ficha del alumno.
   - Reportes de alumnos y asistencia.
   - Comunicados generales para todos los grados.
   - No gestiona pagos: el sistema no tiene módulo de pagos.

3. **`DOCENTE`**
   - Registro de asistencia por bloque y fecha.
   - Planificación de unidades y contenidos de sus cursos.
   - Registro de calificaciones por criterio.
   - Publicación de materiales.
   - Comunicados para sus secciones.
   - Solo accede a sus cursos y secciones asignados.

4. **`ESTUDIANTE`**
   - Consulta de su matrícula, cursos, unidades publicadas, materiales, notas y asistencias.
   - Comunicados generales y de su sección.
   - No registra teléfonos propios.

5. **`APODERADO`**
   - Consulta de sus hijos formalmente vinculados y activos.
   - Notas, asistencias, materiales y comunicados de cada hijo.
   - Creación y seguimiento de tickets de justificación.
   - No puede ver hijos sin vínculo activo.

### 4.2. Matriz de autorización

| Módulo | ADMIN | SECRETARIA | DOCENTE | ESTUDIANTE | APODERADO |
|---|---|---|---|---|---|
| Usuarios y perfiles | CRUD | — | — | — | — |
| Años y bimestres | CRUD | Lectura | Lectura | Lectura | Lectura |
| Materias | CRUD | Lectura | Lectura | Lectura | Lectura |
| Secciones | CRUD | Lectura | Lectura | Lectura | Lectura |
| Cursos y horarios | CRUD | Lectura | Lectura de los suyos | Lectura de los suyos | Lectura de los hijos |
| Matrículas | CRUD | CRUD | — | Lectura propia | Lectura de hijos |
| Planificación (unidades/contenidos) | Lectura | — | CRUD de sus cursos | Lectura publicada | Lectura publicada de hijos |
| Asistencia | Lectura | Lectura | CRUD de sus cursos | Lectura propia | Lectura de hijos |
| Calificaciones | Lectura | Lectura | CRUD de sus cursos | Lectura propia | Lectura de hijos |
| Tickets | — | Resolver/leer | — | — | Crear/leer los suyos |
| Materiales | Lectura (incluye borradores) | — | CRUD de sus cursos | Lectura publicada | Lectura publicada de hijos |
| Comunicados | CRUD general/sección | CRUD general | CRUD de sus secciones | Lectura | Lectura de hijos |
| Cierre y reportes | CRUD | Reportes de alumnos | Actas de sus cursos | Boleta propia | Boleta de hijos |

### 4.3. Reglas de ownership

- **Docente:** el `docenteId` sale del token. Nunca se acepta del request para decidir permisos. Para un `cursoId`, se valida que `curso.docente.id == docenteAutenticado.id`.
- **Estudiante:** el `estudianteId` sale del token. Para un `cursoId`, se valida que el curso pertenezca a la sección de su matrícula vigente.
- **Apoderado:** el `apoderadoId` sale del token. Para un `hijoId`, se valida que exista `ApoderadoEstudiante` con `activo=true`.
- **Secretaría:** puede operar sobre cualquier alumno, pero no sobre la estructura académica (años, bimestres, materias, secciones, cursos, horarios), que es de `ADMIN`.
- **ADMIN:** es superconjunto funcional, pero las reglas de negocio se siguen aplicando. No califica si no es el docente del curso; puede corregir con una acción administrativa auditada si se habilita.
- **Path IDs:** cualquier endpoint con `{id}` valida existencia y pertenencia antes de devolver datos. Un `404` no revela si el recurso existe pero no pertenece al usuario.

### 4.4. Autenticación

- `POST /api/v1/auth/login` (`{email, password}`) → `200` con `{token, tokenType: "Bearer", expiresIn, user}`.
- Límite de intentos: 5 fallos en 15 minutos bloquean 15 minutos; clave `email+IP`; `429` con `Retry-After`; estado en memoria.
- `POST /api/v1/auth/register`: solo con BD vacía; crea el primer `ADMIN`. Luego responde `403`.
- `GET /api/v1/auth/me`: cuenta + perfil del rol en una sola llamada.
- API stateless: `Authorization: Bearer <token>`.
- CORS por propiedad `app.cors.allowed-origins`.
- `@PreAuthorize("hasRole('...')")` en controllers; ownership en services.

---

## 5. Módulos, endpoints y criterios de aceptación

El orden es el orden de implementación recomendado. Cada módulo se cierra (código + tests + Swagger + este documento) antes de pasar al siguiente.

### Módulo 0 — Fundaciones ✅

**Objetivo:** dejar la base técnica lista para el nuevo modelo.

Incluye:
- Docker Compose con PostgreSQL 17 y backend.
- Variables de entorno y `.env.example`.
- `ApiError` y `GlobalExceptionHandler`.
- `Auditable` y auditoría JPA.
- Swagger/OpenAPI.
- CORS.
- CI de GitHub Actions.
- `README.md` con arranque, variables y reset de BD.
- `ddl-auto=update` durante el desarrollo; el esquema se recrea con `docker compose down -v`.

**Reset de desarrollo:**

```bash
docker compose down -v
docker compose up -d --build
./mvnw test
```

**Criterios de aceptación:**
- `docker compose up -d` levanta Postgres y backend sin pasos manuales.
- `./mvnw test` pasa con Testcontainers.
- Swagger responde en `http://localhost:8080/swagger-ui/index.html`.
- No hay secretos versionados.
- `README.md` existe y explica el arranque.

---

### Módulo 1 — Identidad y acceso ✅

**Objetivo:** autenticación JWT, RBAC y gestión de cuentas/perfiles.

**Entidades:** `User`, `Telefono`, `Docente`, `Estudiante`, `Apoderado`, `Secretaria`.

**Reglas:**
- Email único y en minúsculas; DNI único de 8 dígitos.
- Contraseña BCrypt, nunca expuesta.
- Rol inmutable y consistente con el perfil.
- Baja lógica por `User.activo`.
- El primer registro solo funciona con BD vacía y crea `ADMIN`.
- Todas las altas pasan por `UsuarioService.crearCuenta()`.

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `POST /api/v1/auth/login` | público | Login JWT |
| `POST /api/v1/auth/register` | público (solo BD vacía) | Bootstrap del primer ADMIN |
| `GET /api/v1/auth/me` | autenticado | Cuenta + perfil del rol |
| `GET /api/v1/admin/usuarios?rol=&activo=&q=` | ADMIN | Listado de cuentas |
| `GET /api/v1/admin/usuarios/{id}` | ADMIN | Detalle de cuenta |
| `POST /api/v1/admin/usuarios` | ADMIN | Alta de ADMIN |
| `PUT /api/v1/admin/usuarios/{id}` | ADMIN | Edición de cuenta y perfil |
| `PATCH /api/v1/admin/usuarios/{id}/activo` | ADMIN | Baja lógica |
| `PATCH /api/v1/admin/usuarios/{id}/password` | ADMIN | Reseteo de contraseña |
| `POST /api/v1/admin/docentes` | ADMIN | Cuenta + perfil Docente |
| `POST /api/v1/admin/secretarias` | ADMIN | Cuenta + perfil Secretaria |
| `POST /api/v1/secretaria/estudiantes` | ADMIN, SECRETARIA | Cuenta + perfil Estudiante |
| `POST /api/v1/secretaria/apoderados` | ADMIN, SECRETARIA | Cuenta + perfil Apoderado |
| `GET /api/v1/admin/docentes` | ADMIN | Docentes con id de perfil y materias |

**Criterios de aceptación:**
- Login correcto/incorrecto, token expirado, sin token y sin permiso devuelven códigos coherentes.
- No se puede crear un `User` con rol distinto al perfil.
- El alta de estudiante rechaza teléfonos con `400`.
- La baja lógica corta el acceso de inmediato.
- Tests de seguridad para cada zona.

---

### Módulo 2 — Estructura académica anual ✅

**Objetivo:** modelar el año escolar, sus bimestres y la oferta académica.

**Entidades:** `AnioEscolar`, `Bimestre`, `Materia`, `Seccion`, `Curso`, `HorarioBloque`.

**Reglas:**
- Solo un año `ABIERTO` y solo un bimestre `ABIERTO`.
- Un año `PLANIFICADO` permite preparar estructura y es editable; `CERRADO` es solo lectura.
- `Bimestre` no se solapa y vive dentro del año.
- `Seccion` es única por `(año, grado, letra)`.
- `Curso` es único por `(materia, sección)`.
- Materia activa y habilitada al docente.
- Horario sin solapes por sección ni por docente.
- El horario es plantilla; la asistencia se registra por `(bloque, fecha)`.

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET/POST /api/v1/admin/anios` | ADMIN | Listado y alta de años |
| `PUT /api/v1/admin/anios/{id}` | ADMIN | Edición |
| `PATCH /api/v1/admin/anios/{id}/estado` | ADMIN | Planificar/abrir/cerrar |
| `GET/POST /api/v1/admin/anios/{id}/bimestres` | ADMIN | Bimestres del año |
| `PUT /api/v1/admin/bimestres/{id}` | ADMIN | Edición |
| `PATCH /api/v1/admin/bimestres/{id}/estado` | ADMIN | Abrir/cerrar |
| `GET/POST /api/v1/admin/materias` | ADMIN | Catálogo |
| `PUT /api/v1/admin/materias/{id}` | ADMIN | Edición |
| `PATCH /api/v1/admin/materias/{id}/activa` | ADMIN | Archivar/reactivar |
| `GET/POST /api/v1/admin/secciones?anioId=` | ADMIN | Secciones del año |
| `PUT /api/v1/admin/secciones/{id}` | ADMIN | Edición |
| `GET/PUT /api/v1/admin/docentes/{id}/materias` | ADMIN | Habilitaciones |
| `GET/POST /api/v1/admin/cursos?seccionId=&docenteId=` | ADMIN | Cursos |
| `DELETE /api/v1/admin/cursos/{id}` | ADMIN | Baja si no tiene historia |
| `GET/PUT /api/v1/admin/cursos/{id}/horario` | ADMIN | Horario semanal |
| `GET /api/v1/docente/cursos` | DOCENTE | Sus cursos |
| `GET /api/v1/docente/cursos/{id}/horario` | DOCENTE | Horario de su curso |

**Criterios de aceptación:**
- Abrir un segundo año o bimestre devuelve `409`.
- No se puede cerrar un año con bimestres abiertos.
- No se puede mover una sección de año.
- Un curso sin materia habilitada o con materia inactiva devuelve `409`.
- El re-PUT idéntico de horario es un no-op `200`.
- Los solapes de horario devuelven `409`.
- El `PUT` de horario de un curso con asistencias devuelve `409`.

---

### Módulo 3 — Alumnos, apoderados y matrícula ✅

**Objetivo:** registrar alumnos, vincular apoderados y matricularlos en una sección del año.

**Entidades:** `Estudiante`, `Apoderado`, `ApoderadoEstudiante`, `Matricula`.

**Reglas:**
- Solo alumnos `ACTIVO` se matriculan.
- Una `Matricula` por alumno y año.
- La sección debe pertenecer al mismo año que la matrícula.
- La sección puede modificarse solo antes de `AnioEscolar.fechaInicio` (primer día de clases) y mientras no exista ninguna asistencia ni calificación. Al iniciar clases, la matrícula queda fijada.
- El retiro marca `situacionFinal=RETIRADO` y `Estudiante.estado=RETIRADO`.
- El reingreso reactiva al alumno (`ACTIVO`) y su matrícula del año si sigue abierta, o crea una nueva si cambió de año.
- Solo un responsable principal activo por alumno.
- El apoderado no se asigna hijos: los vincula `SECRETARIA`.
- La ficha del alumno se edita de forma acotada: fecha de nacimiento y estado académico.
- No hay módulo de pagos.

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /api/v1/secretaria/estudiantes?q=&estado=` | ADMIN, SECRETARIA | Listado con sección y apoderados |
| `GET /api/v1/secretaria/estudiantes/{id}` | ADMIN, SECRETARIA | Ficha |
| `PUT /api/v1/secretaria/estudiantes/{id}` | ADMIN, SECRETARIA | Edición acotada |
| `PATCH /api/v1/secretaria/estudiantes/{id}/estado` | ADMIN, SECRETARIA | `ACTIVO` (reingreso), `TRASLADADO`, `RETIRADO`, `EGRESADO` |
| `GET /api/v1/secretaria/apoderados?q=` | ADMIN, SECRETARIA | Listado con hijos |
| `GET /api/v1/secretaria/matriculas?anioId=&seccionId=&q=` | ADMIN, SECRETARIA | Matrículas del año/sección |
| `GET /api/v1/secretaria/matriculas/{id}` | ADMIN, SECRETARIA | Detalle de matrícula |
| `POST /api/v1/secretaria/matriculas` | ADMIN, SECRETARIA | Crea matrícula en una sección |
| `PATCH /api/v1/secretaria/matriculas/{id}/seccion` | ADMIN, SECRETARIA | Cambia de sección antes de iniciar clases |
| `PATCH /api/v1/secretaria/matriculas/{id}/retiro` | ADMIN, SECRETARIA | Retiro del año |
| `POST/DELETE /api/v1/secretaria/apoderados/{id}/representados` | ADMIN, SECRETARIA | Vincular/desvincular |
| `GET /api/v1/apoderado/hijos` | APODERADO | Sus hijos activos con sección vigente |
| `GET /api/v1/estudiante/matricula` | ESTUDIANTE | Su matrícula y sección vigente |

**Criterios de aceptación:**
- Matricular dos veces al mismo alumno en el año devuelve `409`.
- Matricular en una sección de otro año devuelve `409`.
- Cambiar de sección con el año iniciado o con asistencias/calificaciones devuelve `409`.
- Cambiar de sección antes de `AnioEscolar.fechaInicio` y sin actividad devuelve `200`.
- Un retiro impide nuevas asistencias y notas.
- Un apoderado sin vínculo activo no ve al alumno.
- Solo un responsable principal activo por alumno.

---

### Módulo 4 — Planificación docente ✅

**Objetivo:** que el docente planifique sus cursos por bimestre con unidades y contenidos.

**Entidades:** `Unidad`, `Contenido`.

**Reglas:**
- Solo el docente del curso crea/edita sus unidades.
- La unidad pertenece a un bimestre del mismo año que la sección del curso.
- Se puede planificar en bimestre `PLANIFICADO` o `ABIERTO`; no en `CERRADO`.
- El alumno y el apoderado solo ven unidades `PUBLICADA`.
- Los contenidos se reordenan con un endpoint bulk.
- No se elimina una unidad con calificaciones o contenidos con materiales.
- No se elimina un contenido con materiales.

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET/POST /api/v1/docente/cursos/{cursoId}/unidades` | DOCENTE | Unidades del curso |
| `GET/PUT/DELETE /api/v1/docente/unidades/{id}` | DOCENTE | Detalle/edición/baja |
| `PATCH /api/v1/docente/unidades/{id}/estado` | DOCENTE | Publicar/despublicar |
| `GET/POST /api/v1/docente/unidades/{id}/contenidos` | DOCENTE | Contenidos |
| `PUT/DELETE /api/v1/docente/contenidos/{id}` | DOCENTE | Edición/baja |
| `PUT /api/v1/docente/unidades/{id}/contenidos/orden` | DOCENTE | Reordenar |
| `GET /api/v1/estudiante/cursos/{cursoId}/unidades` | ESTUDIANTE | Solo publicadas |
| `GET /api/v1/apoderado/hijos/{id}/cursos/{cursoId}/unidades` | APODERADO | Solo publicadas |
| `GET /api/v1/admin/cursos/{cursoId}/unidades` | ADMIN | Lectura, incluye borradores |
| `GET /api/v1/admin/unidades/{id}` | ADMIN | Lectura, incluye borradores |

**Criterios de aceptación:**
- Un docente no puede ver ni editar unidades de un curso ajeno.
- Un estudiante no puede ver unidades `BORRADOR`.
- El número de la unidad y el orden del contenido son automáticos.
- El reordenamiento no viola el unique `(unidad_id, orden)`.
- Publicar una unidad con contenidos los hace visibles.
- ADMIN sólo lee la planificación, incluidos los borradores.

---

### Módulo 5 — Asistencia ✅

**Objetivo:** registrar asistencia por bloque de horario y fecha.

**Entidades:** `Asistencia`.

**Reglas:**
- La asistencia se identifica por `(matricula_id, bloque_id, fecha)`.
- El bloque debe pertenecer a un curso de la sección de la matrícula.
- La fecha debe caer en el día del bloque y dentro de un bimestre `ABIERTO` del año de la matrícula.
- No se registra asistencia de una fecha futura (`400`).
- Solo el docente dueño del curso registra o corrige; un curso ajeno responde `404`.
- El docente no envía el estado: envía `asistio` (verdadero/falso). El sistema guarda `PRESENTE` o `FALTA`.
- El sistema deriva `TARDANZA` con la hora del servidor: más de **15 minutos** después de `HorarioBloque.horaInicio`. Para fechas pasadas se asume `PRESENTE`, porque no se puede reconstruir la hora de llegada.
- El docente no marca `JUSTIFICADA`: ese estado nace en M7 cuando Secretaría aprueba un ticket. En M5 el enum existe pero no es alcanzable desde la API; una asistencia ya `JUSTIFICADA` no se puede pisar (`409`).
- El `PUT` es un **upsert parcial** por bloque y fecha: solo viajan los alumnos que se registran o corrigen; los ausentes conservan su estado.
- El `GET` de la clase devuelve a **todos** los alumnos activos de la sección; los no registrados llegan con `estado: null`.
- No se borra una asistencia; se corrige y queda la auditoría (`updatedAt`, `registradoPor`).
- Si la matrícula ya tiene asistencias, no se puede cambiar de sección (`409`).
- Si el curso ya tiene asistencias, no se puede reemplazar su horario ni eliminarlo (`409`).
- No hay reprogramaciones: si la clase se mueve de fecha, no se representa.

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /api/v1/docente/cursos/{cursoId}/asistencia?bloqueId=&fecha=` | DOCENTE | Hoja de la clase: todos los alumnos, `estado` nullable |
| `PUT /api/v1/docente/cursos/{cursoId}/asistencia` | DOCENTE | Upsert parcial de la hoja |
| `GET /api/v1/estudiante/asistencias?anioId=&bimestreId=` | ESTUDIANTE | Historial y resumen propios |
| `GET /api/v1/apoderado/hijos/{hijoId}/asistencias?anioId=&bimestreId=` | APODERADO | Historial y resumen del hijo vinculado |
| `GET /api/v1/secretaria/estudiantes/{id}/asistencias?anioId=&bimestreId=` | SECRETARIA | Historial y resumen del alumno |
| `GET /api/v1/admin/estudiantes/{id}/asistencias?anioId=&bimestreId=` | ADMIN | Lectura del historial |
| `GET /api/v1/admin/cursos/{cursoId}/asistencia?bloqueId=&fecha=` | ADMIN | Lectura de la hoja de clase |

Cuerpo del `PUT`:

```json
{
  "bloqueId": 5,
  "fecha": "2026-03-09",
  "asistencias": [
    { "matriculaId": 21, "asistio": true },
    { "matriculaId": 22, "asistio": false, "observacion": "Sin aviso" }
  ]
}
```

**Resumen de asistencia:**

```text
efectivas  = PRESENTE + TARDANZA + JUSTIFICADA
porcentaje = efectivas / asistencias registradas × 100
```

Solo `FALTA` es inasistencia injustificada. El mínimo para aprobar es `70%`. Sin registros, el porcentaje es `0.00`.

**Criterios de aceptación:**
- No se registra asistencia en bimestre cerrado ni en año cerrado (`409`).
- No se registra asistencia de un alumno de otra sección ni de una matrícula retirada.
- Una fecha futura o que no corresponde al día del bloque devuelve `400`.
- El docente no puede enviar `TARDANZA` ni `JUSTIFICADA`; el estado se deriva o se reserva a M7.
- Un `PUT` parcial no borra ni altera a los alumnos que no aparecen en el cuerpo.
- La hoja de clase incluye a los alumnos sin registro con `estado: null`.
- El resumen devuelve total, presentes, tardanzas, justificadas, faltas, efectivas y porcentaje.
- Estudiante, apoderado, secretaría y admin solo leen lo que su rol permite.
- `TARDANZA` se deriva pasados 15 minutos del inicio del bloque.
- Con asistencias registradas, reemplazar el horario o eliminar el curso responde `409`.

---

### Módulo 6 — Calificaciones ✅

**Objetivo:** registrar notas vigesimales por unidad y criterio.

**Entidades:** `Calificacion`.

**Reglas:**
- La calificación se identifica por `(matricula_id, unidad_id, criterio)`.
- El `criterio` es texto libre de hasta 120 caracteres: "Examen parcial", "Práctica 1", "Tarea 2". Se guarda con `trim`.
- Solo el docente dueño del curso registra, corrige o elimina; una unidad o curso ajeno responde `404`.
- La matrícula debe pertenecer a la sección del curso de la unidad y no estar retirada.
- El bimestre de la unidad debe estar `ABIERTO` y el año no `CERRADO`; si no, `409`.
- La nota es vigesimal estricta, entre `0.00` y `20.00`, con máximo dos decimales; fuera de rango o con más decimales responde `400`.
- El `PUT` es un **upsert parcial** por `(matrícula, criterio)`: solo viajan las notas que se registran o corrigen; las demás no se tocan.
- La unidad puede estar en `BORRADOR` o `PUBLICADA` para calificar. El alumno y el apoderado sólo ven notas de unidades `PUBLICADA`; docente, secretaría y admin ven también los borradores.
- La baja es **física** y sólo se permite con el bimestre `ABIERTO`.
- No hay entidad `Evaluacion`: el criterio vive en la calificación.
- Los promedios se calculan por consulta; no se guardan snapshots.
- Con notas registradas, no se despublica ni elimina la unidad (`409`), no se elimina el curso (`409`) y no se cambia de sección la matrícula (`409`).

**Promedios:**
- Unidad: media simple de sus notas.
- Bimestre (por curso): media simple de las unidades con notas.
- Anual (por curso): media simple de los bimestres con notas.
- General: media simple de los promedios anuales de los cursos con notas.
- Redondeo `HALF_UP` a 2 decimales; sin notas, el promedio es `null`.
- Aprobación del curso: anual `≥ 11.00`. Aprobación del grado: todos los cursos aprobados y asistencia global `≥ 70%`.

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /api/v1/docente/unidades/{id}/calificaciones` | DOCENTE | Hoja de la unidad: todos los alumnos activos |
| `PUT /api/v1/docente/unidades/{id}/calificaciones` | DOCENTE | Upsert parcial por (matrícula, criterio) |
| `DELETE /api/v1/docente/calificaciones/{id}` | DOCENTE | Baja física en bimestre `ABIERTO` |
| `GET /api/v1/docente/cursos/{id}/actas?bimestreId=` | DOCENTE | Acta: alumno × unidades + promedio |
| `GET /api/v1/estudiante/calificaciones?anioId=&bimestreId=` | ESTUDIANTE | Propias + promedios |
| `GET /api/v1/apoderado/hijos/{id}/calificaciones?anioId=&bimestreId=` | APODERADO | Del hijo + promedios |
| `GET /api/v1/secretaria/estudiantes/{id}/calificaciones?anioId=&bimestreId=` | SECRETARIA | Del alumno |
| `GET /api/v1/admin/estudiantes/{id}/calificaciones?anioId=&bimestreId=` | ADMIN | Lectura |
| `GET /api/v1/admin/cursos/{id}/actas?bimestreId=` | ADMIN | Lectura del acta |

Cuerpo del `PUT`:

```json
{
  "calificaciones": [
    { "matriculaId": 21, "criterio": "Examen parcial", "nota": 15.50 },
    { "matriculaId": 21, "criterio": "Práctica 1", "nota": 14.00 }
  ]
}
```

**Criterios de aceptación:**
- No se califica en bimestre cerrado ni en año cerrado (`409`).
- No se califica a un alumno de otra sección ni a una matrícula retirada.
- Nota fuera de rango o con más de dos decimales devuelve `400`.
- Un docente no ve ni califica unidades de cursos ajenos (`404`).
- Repetir `(matrícula, criterio)` en el mismo `PUT` devuelve `400`.
- Un `PUT` parcial no altera las notas que no aparecen en el cuerpo.
- El alumno y el apoderado no ven notas de unidades en `BORRADOR`; secretaría y admin sí.
- El promedio de unidad, bimestre, año y general coincide con las fórmulas documentadas.
- El acta devuelve una fila por alumno, una columna por unidad y el promedio del bimestre.
- Con notas registradas, despublicar/eliminar la unidad o eliminar el curso responde `409`.
- El cambio de sección se bloquea si la matrícula ya tiene notas (`409`).

---

### Módulo 7 — Justificaciones ✅

**Objetivo:** que el apoderado justifique inasistencias y Secretaría las resuelva.

**Entidades:** `TicketJustificacion`, `Asistencia` (se agrega `ticket_id`).

**Reglas:**
- Solo el apoderado con vínculo activo crea tickets de su representado.
- La fecha no puede ser futura.
- Un ticket puede ser de todo el día (`bloque_id` nulo) o de un bloque.
- Si hay bloque, debe ser de la sección de la matrícula del alumno y la fecha debe caer en el día de ese bloque.
- Secretaría resuelve; al aprobar se actualizan las faltas del alcance en la misma transacción.
- Solo se resuelve desde `PENDIENTE`; no hay vuelta atrás.
- Si la asistencia se registra después de aprobar, nace `JUSTIFICADA` y vinculada al ticket.
- No se permite más de un ticket activo por alumno/fecha/bloque; el alcance de día completo prevalece.
- Un ticket `RECHAZADO` ya no bloquea uno nuevo.
- No se elimina; los estados son la historia.
- ADMIN no accede a la bandeja: resolver es exclusivo de SECRETARIA.

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /api/v1/apoderado/hijos/{id}/tickets?anioId=&estado=` | APODERADO | Historial del hijo |
| `POST /api/v1/apoderado/hijos/{id}/tickets` | APODERADO | Crea ticket `PENDIENTE` |
| `GET /api/v1/secretaria/tickets?estado=&fecha=` | SECRETARIA | Bandeja |
| `GET /api/v1/secretaria/tickets/{id}` | SECRETARIA | Detalle |
| `PATCH /api/v1/secretaria/tickets/{id}/resolver` | SECRETARIA | Aprueba o rechaza |

Cuerpo del `POST`:

```json
{
  "fechaInasistencia": "2026-03-09",
  "bloqueId": null,
  "motivo": "Cita médica"
}
```

> El sustento no viaja en el alta: se sube aparte con `POST /api/v1/apoderado/tickets/{id}/sustento` (multipart). La respuesta del ticket expone `storagePath` (ruta privada), no una URL.

Cuerpo del `PATCH .../resolver`:

```json
{
  "estado": "APROBADO",
  "observacionesAdmin": "Documento válido"
}
```

**Criterios de aceptación:**
- Un apoderado no ve ni crea tickets de hijos ajenos (`404`).
- Una fecha futura, un motivo vacío o una fecha que no es el día del bloque devuelven `400`.
- Un bloque de otra sección devuelve `404`.
- Un ticket activo que se solapa devuelve `409`; dos bloques distintos del mismo día conviven.
- Aprobar un ticket actualiza todas las faltas del alcance en una transacción.
- Rechazar no toca la asistencia.
- Un ticket ya resuelto no se vuelve a resolver (`409`).
- Una falta registrada después de aprobar nace `JUSTIFICADA`.
- ADMIN no entra a la bandeja de Secretaría (`403`).

---

### Módulo 8 — Materiales y Storage ✅

**Objetivo:** publicar material didáctico y sustentos en Supabase Storage.

**Entidades:** `MaterialClase` (nueva) y `TicketJustificacion` (se le cambia `urlArchivoSustento` por `storagePath` y se le añade el upload de sustento).

**Reglas:**
- Buckets privados: `materiales` y `sustentos`.
- El backend sube con service role; nunca expone esa clave al frontend.
- Se guarda `storagePath`, no una URL pública permanente.
- La descarga se hace con URL firmada de corta duración (5 minutos).
- Solo el docente del curso sube material.
- Material no publicado solo lo ve el docente y `ADMIN`.
- Al crear, el material queda `publicado=false`; se publica con un `PATCH` explícito.
- Validar MIME, extensión y tamaño; extensión y MIME deben coincidir.
- Rutas (sin el nombre del bucket, que va aparte): `{cursoId}/{uuid}-{nombreSeguro}` en `materiales` y `{ticketId}/{uuid}-{nombreSeguro}` en `sustentos`. El nombre se sanea y se recorta a 120 caracteres.
- Materiales permitidos: PDF, DOC/DOCX, PPT/PPTX, XLS/XLSX, JPG/JPEG/PNG y MP4; máximo 25 MB.
- Sustentos permitidos: PDF, JPG/JPEG/PNG; máximo 5 MB. El sustento es opcional y solo se adjunta/reemplaza mientras el ticket está `PENDIENTE`.
- La baja de material es lógica (`activo=false`, `publicado=false`) y elimina el objeto del bucket en la misma operación; si Storage falla, el material queda inactivo y no accesible.
- Reemplazar el archivo de un material elimina el objeto anterior del bucket.
- Un material inactivo no se lista ni se descarga, ni siquiera para `ADMIN`.
- M8 completa los guards pendientes: no se elimina un curso, unidad o contenido con materiales (`409`).

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET/POST /api/v1/docente/cursos/{id}/materiales` | DOCENTE | Listado (incluye no publicados) / alta multipart |
| `PUT /api/v1/docente/materiales/{id}` | DOCENTE | Edita metadatos (título, descripción, contenido) |
| `PUT /api/v1/docente/materiales/{id}/archivo` | DOCENTE | Reemplaza el archivo (multipart) |
| `PATCH /api/v1/docente/materiales/{id}/publicacion` | DOCENTE | Publica/despublica (`{publicado: true\|false}`) |
| `DELETE /api/v1/docente/materiales/{id}` | DOCENTE | Baja lógica |
| `GET /api/v1/admin/cursos/{id}/materiales` | ADMIN | Lectura, incluye no publicados |
| `GET /api/v1/estudiante/cursos/{id}/materiales` | ESTUDIANTE | Solo publicados y activos |
| `GET /api/v1/apoderado/hijos/{id}/cursos/{id}/materiales` | APODERADO | Solo publicados y activos |
| `GET /api/v1/materiales/{id}/download` | según permisos | URL firmada de 5 minutos |
| `POST /api/v1/apoderado/tickets/{id}/sustento` | APODERADO | Sube/reemplaza sustento del propio ticket |
| `GET /api/v1/apoderado/tickets/{id}/sustento` | APODERADO | URL firmada del sustento propio |
| `GET /api/v1/secretaria/tickets/{id}/sustento` | SECRETARIA | URL firmada del sustento |

**Contratos:**

- Alta de material: `multipart/form-data` con `archivo` (obligatorio), `titulo` (obligatorio), `descripcion` y `contenidoId` (opcionales). Responde el material con `publicado=false`.
- Edición de metadatos: JSON `{"titulo":"...","descripcion":"...","contenidoId":null}`. No cambia el archivo.
- Reemplazo de archivo: `multipart/form-data` con `archivo` (obligatorio).
- Publicación: `{"publicado":true}` o `{"publicado":false}`.
- Descarga: responde `{"url":"https://...","expiresAt":"2026-11-04T10:05:00"}`; la URL dura 5 minutos.
- Sustento: `multipart/form-data` con `archivo` (obligatorio). Responde el ticket con `storagePath` no nulo.
- Errores de archivo: `400` por MIME/extensión inválida o archivo vacío; `413` por tamaño excedido; `404` por recurso ajeno o inactivo.

**Criterios de aceptación:**
- Un alumno no descarga material no publicado, inactivo o de una unidad `BORRADOR`.
- Un docente no sube material a un curso ajeno.
- `ADMIN` lista y descarga materiales no publicados, pero no los modifica.
- Publicar y despublicar actualiza `fechaPublicacion` y el acceso del alumno.
- La URL firmada expira a los 5 minutos.
- La baja lógica no deja el archivo accesible y elimina el objeto del bucket.
- Un apoderado no adjunta sustento a un ticket ajeno ni a uno ya resuelto.
- `SECRETARIA` descarga el sustento; `ADMIN` no accede a tickets.

---

### Módulo 9 — Comunicados ✅

**Objetivo:** mensajes simples del docente a su sección, de Secretaría a toda la institución y del ADMIN a toda la institución o a una sección.

**Entidades:** `Comunicado`.

**Reglas:**
- `ADMIN` publica mensajes generales (`seccion_id=NULL`) o para una sección.
- `SECRETARIA` publica solo mensajes generales (`seccion_id=NULL`), para todos los grados.
- `DOCENTE` publica solo en secciones donde tiene cursos o tutoría.
- El alumno ve los generales y los de su sección.
- El apoderado ve los generales y los de la sección de cada hijo activo.
- No hay `tipo` ni `alcance` separados: el filtro es `seccion_id` (`NULL` = general).
- El mensaje guarda autor, fecha/hora y contenido; no requiere título.
- Baja lógica.

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET/POST /api/v1/admin/comunicados` | ADMIN | General o por sección |
| `PUT/DELETE /api/v1/admin/comunicados/{id}` | ADMIN | Edición/baja |
| `GET/POST /api/v1/secretaria/comunicados` | SECRETARIA | Lee generales; publica generales |
| `PUT/DELETE /api/v1/secretaria/comunicados/{id}` | SECRETARIA | Edición/baja de sus generales |
| `GET/POST /api/v1/docente/comunicados` | DOCENTE | Lee generales y sus secciones; publica en las suyas |
| `PUT/DELETE /api/v1/docente/comunicados/{id}` | DOCENTE | Edición/baja de los suyos |
| `GET /api/v1/estudiante/comunicados` | ESTUDIANTE | Visibles |
| `GET /api/v1/apoderado/comunicados` | APODERADO | De sus hijos |

> Los listados de `ADMIN`, `SECRETARIA` y `DOCENTE` aceptan filtros opcionales `anioId`; `ADMIN` y `DOCENTE` aceptan además `seccionId`. Los endpoints de lectura de alumno y apoderado no reciben filtros: el servidor resuelve la sección vigente.

**Criterios de aceptación:**
- Un docente no publica en una sección ajena.
- Secretaría no puede publicar en una sección; solo mensajes generales.
- Secretaría y docente solo editan/eliminan sus propios comunicados; el ajeno responde `404`.
- Un alumno no ve comunicados de otra sección.
- Un apoderado solo ve los de las secciones de sus hijos activos con matrícula vigente.
- Un comunicado inactivo no aparece.
- Un año `CERRADO` no admite altas, ediciones ni bajas (`409`).

---

### Módulo 10 — Cierre y reportes ✅

**Objetivo:** cerrar el año, fijar `situacionFinal` y emitir boletas/actas.

**Entidades:** `Matricula`, `Calificacion`, `Asistencia`, `Bimestre`, `AnioEscolar`.

**Reglas:**
- El cierre de año requiere todos los bimestres cerrados.
- `situacionFinal`:
  - `APROBADO` si **todos** los cursos tienen promedio anual ≥ `11.00` y la asistencia global del alumno ≥ `70%`.
  - `DESAPROBADO` si algún curso tiene promedio anual < `11.00` o la asistencia global < `70%`.
  - `RETIRADO` si el alumno se retiró.
- Un curso desaprobado impide `APROBADO` aunque el promedio general sea mayor o igual a `11.00`.
- Al aprobar 5° grado, `Estudiante.estado=EGRESADO`.
- El cierre es idempotente: si el año ya está `CERRADO`, el `POST` devuelve el preview sin volver a aplicar efectos.
- Las boletas, el historial y los reportes se calculan, no se guardan.
- Los cálculos usan solo notas de unidades `PUBLICADA`, igual que la vista del alumno.
- Si falta el promedio anual de algún curso de un alumno no retirado, el cierre se bloquea y requiere revisión de `ADMIN`.
- Un alumno `RETIRADO` conserva `RETIRADO` y no bloquea el cierre por notas.
- La asistencia global sin registros es `0.00%` y por tanto `DESAPROBADO`.
- Los reportes exigen `anioId`; `bimestreId` y `seccionId` son opcionales y deben pertenecer al año (`409`).
- La boleta sin `anioId` usa la matrícula del año `ABIERTO`; con el año cerrado debe enviarse `anioId`.
- El historial de Secretaría también admite `ADMIN` (`hasAnyRole('ADMIN','SECRETARIA')`).

**Endpoints:**

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /api/v1/admin/anios/{id}/cierre/preview` | ADMIN | Vista previa |
| `POST /api/v1/admin/anios/{id}/cerrar` | ADMIN | Cierre del año |
| `GET /api/v1/estudiante/boleta?anioId=` | ESTUDIANTE | Boleta propia |
| `GET /api/v1/apoderado/hijos/{id}/boleta?anioId=` | APODERADO | Boleta del hijo |
| `GET /api/v1/secretaria/estudiantes/{id}/historial` | SECRETARIA / ADMIN | Historial |
| `GET /api/v1/admin/reportes/asistencia?anioId=&bimestreId=&seccionId=` | ADMIN | Reporte |
| `GET /api/v1/admin/reportes/calificaciones?anioId=&bimestreId=&seccionId=` | ADMIN | Reporte |

**Criterios de aceptación:**
- No se cierra un año con bimestres abiertos.
- El preview coincide con el cierre.
- Un curso con promedio anual < `11.00` impide `APROBADO` aunque el promedio general sea mayor.
- Un alumno retirado no aparece como aprobado y conserva `RETIRADO`.
- Un alumno sin registros de asistencia queda `DESAPROBADO`.
- Repetir el cierre de un año `CERRADO` responde `200` sin duplicar efectos.
- La boleta de un apoderado solo incluye hijos activos.
- Un reporte con `anioId` ausente responde `400`; con bimestre o sección de otro año, `409`.

---

### Módulo 11 — Endurecimiento ✅

**Objetivo:** dejar el sistema listo para producción.

Implementado:
- Flyway en todos los entornos y `spring.jpa.hibernate.ddl-auto=validate`.
- `V1__esquema_inicial.sql` con el esquema completo.
- `V2__datos_demo.sql` con actores y escenario mínimo, solo en desarrollo.
- Docker non-root y `HEALTHCHECK` contra `/actuator/health`.
- Actuator expone únicamente `health`.
- CI ejecuta `./mvnw -B test` en push/PR a `main` y `develop`.

Diferido y documentado como limitación:
- Refresh tokens o rotación de JWT.
- Rate limiting distribuido (Redis) para múltiples instancias.
- Backups automáticos de PostgreSQL y prueba de restore.
- Métricas, logs estructurados y alertas.
- Revisión de seguridad de Storage y CORS.

**Criterios de aceptación:**
- El esquema se migra desde cero con Flyway. ✅
- Hibernate valida el esquema (`ddl-auto=validate`). ✅
- No hay secretos en Git. ✅
- El contenedor corre con usuario no root. ✅
- El pipeline falla si los tests fallan. ✅
- Los datos demo no se cargan en producción. ✅

---

## 6. Flujos principales

### 6.1. Bootstrap del sistema

1. Se levanta la BD vacía y Flyway aplica el esquema.
2. Sin datos demo, `POST /api/v1/auth/register` crea el primer `ADMIN`.
3. A partir de ahí, el endpoint responde `403`.
4. El `ADMIN` crea docentes, secretarias y la estructura del año.

> En desarrollo, `db/seed/V2__datos_demo.sql` ya crea los cinco actores y un escenario mínimo; las credenciales están en `docs/CREDENCIALES.md`.

### 6.2. Preparación de un año escolar

1. `ADMIN` crea `AnioEscolar` en `PLANIFICADO`.
2. Crea los 4 `Bimestre` en `PLANIFICADO`.
3. Crea `Seccion` (3°A, 3°B...).
4. Crea `Curso` (materia × sección × docente).
5. Define `HorarioBloque`.
6. Cuando todo está listo, abre el año (`ABIERTO`) y el primer bimestre.
7. El año anterior queda `CERRADO` y solo lectura.

### 6.3. Matrícula de un alumno

1. `SECRETARIA` registra la cuenta y el perfil del alumno.
2. Registra o vincula al apoderado.
3. Crea `Matricula` para el año en una sección, en `EN_CURSO`.
4. La sección puede modificarse antes de `AnioEscolar.fechaInicio` y sin actividad; al iniciar clases, queda fijada.
5. Si hay retiro: marca `situacionFinal=RETIRADO` y `Estudiante.estado=RETIRADO`.

### 6.4. Planificación de una unidad

1. `DOCENTE` entra a uno de sus cursos.
2. Crea `Unidad` en `BORRADOR` para el bimestre vigente o planificado.
3. Añade `Contenido` ordenados.
4. Publica la unidad cuando está lista.
5. El alumno y el apoderado ven la unidad publicada.

### 6.5. Asistencia de una clase

1. `DOCENTE` selecciona un `HorarioBloque` y una fecha.
2. El sistema valida que la fecha caiga en el día del bloque y en un bimestre `ABIERTO`.
3. Carga la lista de alumnos de la sección.
4. Registra `PRESENTE`, `TARDANZA` o `FALTA` para cada uno.
5. Si hay ticket aprobado, la falta se guarda como `JUSTIFICADA`.

### 6.6. Calificación de una unidad

1. `DOCENTE` abre una unidad de su curso.
2. Define un `criterio` ("Examen parcial", "Práctica 1").
3. Carga la lista de alumnos de la sección.
4. Registra notas `0.00`–`20.00`.
5. El sistema calcula promedios simples de unidad y bimestre.
6. El alumno y el apoderado ven las notas si la unidad está publicada.

### 6.7. Justificación de inasistencia

1. `APODERADO` crea un ticket para un hijo, con fecha y sustento.
2. `SECRETARIA` revisa el sustento.
3. Si aprueba, las faltas del alcance pasan a `JUSTIFICADA` en la misma transacción.
4. Si rechaza, la asistencia no cambia.
5. Si la asistencia aún no existía, se justificará al registrarse.

### 6.8. Cierre de bimestre y año

1. `ADMIN` cierra el bimestre.
2. Se bloquean nuevas notas y asistencias de ese bimestre.
3. Si no es el IV bimestre, se abre el siguiente.
4. Con todos los bimestres cerrados, `ADMIN` ejecuta `POST /admin/anios/{id}/cerrar` (acción manual, no automática).
5. Se calcula `situacionFinal` de cada matrícula: `APROBADO` solo si todos los cursos están aprobados y la asistencia global es ≥ `70%`.
6. Se marca `EGRESADO` a los alumnos de 5° aprobados.
7. El año queda `CERRADO` y solo lectura.

---

## 7. Base de datos y migraciones

### 7.1. Estrategia actual (M11)

- El esquema lo gobierna **Flyway**; Hibernate solo valida (`spring.jpa.hibernate.ddl-auto=validate`).
- `db/migration/V1__esquema_inicial.sql` contiene el esquema completo.
- `db/seed/V2__datos_demo.sql` contiene datos mínimos de demostración y **solo se carga en desarrollo**.
- `FLYWAY_LOCATIONS` decide qué migraciones se aplican:
  - desarrollo: `classpath:db/migration,classpath:db/seed`;
  - producción: `classpath:db/migration`.
- Los tests de integración usan el perfil `test`, que carga solo `db/migration`, y Testcontainers; no tocan la BD de desarrollo.
- Para recrear la BD de desarrollo:

```bash
docker compose down -v
docker compose up -d --build
```

- No se versionan volúmenes ni datos reales.

### 7.2. Reglas de migración

```text
src/main/resources/db/migration/
  V1__esquema_inicial.sql
src/main/resources/db/seed/
  V2__datos_demo.sql
```

- Una migración ya aplicada **no se edita**; se agrega una nueva versión (`V3__...`).
- `ddl-auto=validate` en todos los entornos: si una entidad no coincide con el esquema, la aplicación no arranca.
- Ningún cambio de esquema se hace a mano en producción.
- Los índices, checks y constraints viven en el SQL de la migración.
- La migración se prueba desde cero sobre Testcontainers y sobre una BD vacía antes de desplegar.
- Las contraseñas de los datos demo se documentan en `docs/CREDENCIALES.md`; no se cargan en producción.

### 7.3. Convenciones de esquema

- Tablas en plural, snake_case: `anios_escolares`, `horario_bloques`, `calificaciones`.
- FKs `<entidad>_id`.
- Enums como `VARCHAR` + `CHECK`, no ordinales.
- Timestamps `created_at`, `updated_at`.
- Borrado lógico con `activo` donde aplique.
- No se usan nombres reservados de SQL.

### 7.4. Datos semilla

Para desarrollo se puede cargar un año 2026, 4 bimestres, secciones 1°A–5°A, materias base y un usuario por rol. Los datos semilla no se ejecutan en producción.

---

## 8. Convenciones de API

### 8.1. Prefijo y versionado

- Base: `/api/v1`.
- Cambios breaking se hacen con `/api/v2` o con una migración coordinada del SPA.
- El reemplazo de `/admin/periodos` por `/admin/anios` es breaking y debe coordinarse con el frontend.

### 8.2. Formato

- JSON en camelCase.
- Fechas ISO-8601 (`YYYY-MM-DD` para `LocalDate`, `YYYY-MM-DDTHH:mm:ss` para `LocalDateTime`).
- Notas como número decimal con dos decimales.
- Booleanos reales, no strings.
- No se devuelven entidades JPA; solo DTOs.

### 8.3. Paginación

Listados grandes usan:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Parámetros: `page`, `size`, `sort`. Los listados pequeños pueden devolver arrays, pero deben documentarlo.

### 8.4. Semántica HTTP

- `GET`: lectura, sin efectos.
- `POST`: creación o acción no idempotente.
- `PUT`: reemplazo completo (habilitaciones, horario, asistencia bulk, notas bulk).
- `PATCH`: cambio parcial de estado o campo.
- `DELETE`: baja física solo cuando no hay historia; si hay historia, `PATCH` de estado.

### 8.5. Swagger

- Cada endpoint documenta rol, request, response y códigos de error.
- Los DTOs tienen ejemplos.
- Las reglas de negocio relevantes se anotan en la descripción.

---

## 9. Testing y CI

### 9.1. Tipos de prueba

| Tipo | Herramienta | Qué cubre |
|---|---|---|
| Unitarias | JUnit 5 | Reglas de dominio, cálculos, estados |
| Integración | Testcontainers PostgreSQL 17 | Repositorios, constraints, transacciones |
| Web | MockMvc | Controllers, validación, seguridad |
| Seguridad | MockMvc + JWT | RBAC y ownership |
| Migraciones | Flyway + Testcontainers | Solo en M11 |

### 9.2. Reglas

- Cada módulo debe tener tests de sus reglas críticas.
- Los tests no tocan la BD de desarrollo.
- Los tests de integración usan una PostgreSQL efímera por clase o contenedor compartido.
- Los tests de constraints prueban los `409` y `400`.
- Los tests de ownership prueban que un usuario no accede a recursos ajenos.
- El CI ejecuta `./mvnw test` en cada push y en PR a `main`.

### 9.3. Cobertura mínima esperada

No se persigue un porcentaje ciego, pero sí:
- Toda regla de negocio documentada tiene al menos un test.
- Todo endpoint tiene test de éxito y de error principal.
- Todo estado y transición crítica tiene test.

---

## 10. Operación y entorno

### 10.1. Docker Compose

Servicios:
- `postgres`: PostgreSQL 17, accesible solo en `127.0.0.1`.
- `backend`: Spring Boot en `http://localhost:8080`.

Comandos:

```bash
docker compose up -d
docker compose down
docker compose down -v      # borra volumen de BD
./mvnw spring-boot:run      # desarrollo con recarga
./mvnw test                 # tests con Testcontainers
```

### 10.2. Variables de entorno

| Variable | Uso |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC |
| `SPRING_DATASOURCE_USERNAME` | Usuario BD |
| `SPRING_DATASOURCE_PASSWORD` | Password BD |
| `APP_JWT_SECRET` | Secreto JWT (obligatorio) |
| `APP_JWT_EXPIRATION_MS` | Expiración del token en milisegundos |
| `APP_SECURITY_LOGIN_MAX_ATTEMPTS` | Intentos de login |
| `APP_SECURITY_LOGIN_WINDOW_MINUTES` | Ventana de intentos |
| `APP_SECURITY_LOGIN_COOLDOWN_MINUTES` | Duración del bloqueo |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes del SPA |
| `POSTGRES_DB` | Nombre de la BD en Docker |
| `POSTGRES_USER` | Usuario de la BD en Docker |
| `POSTGRES_PASSWORD` | Password de la BD en Docker |
| `POSTGRES_PORT` | Puerto host de PostgreSQL |
| `BACKEND_PORT` | Puerto host del backend |
| `SUPABASE_URL` | URL del proyecto Supabase |
| `SUPABASE_SERVICE_ROLE_KEY` | Clave de servicio (solo backend) |
| `SUPABASE_BUCKET_MATERIALES` | Bucket de materiales |
| `SUPABASE_BUCKET_SUSTENTOS` | Bucket de sustentos |

Reglas:
- `.env` no se versiona; `.env.example` sí.
- En Docker, el backend no arranca sin `APP_JWT_SECRET`; `application.properties` conserva un default de desarrollo que debe eliminarse antes de producción.
- La service role key nunca llega al frontend.
- Postgres solo escucha en `127.0.0.1`.

### 10.3. Supabase Storage

- Buckets privados: `materiales` y `sustentos`.
- El backend sube con service role y firma URLs de descarga de 5 minutos.
- Rutas: `{cursoId}/{uuid}-{nombreSeguro}` en el bucket `materiales` y `{ticketId}/{uuid}-{nombreSeguro}` en el bucket `sustentos`.
- Se validan MIME, extensión y tamaño; extensión y MIME deben coincidir.
- Materiales: máximo 25 MB (PDF, Office, imágenes y MP4). Sustentos: máximo 5 MB (PDF e imágenes).
- La baja de material es lógica en BD y elimina el objeto del bucket; si Storage falla, el material queda inactivo e inaccesible.

### 10.4. Logs y salud

- Logs estructurados con nivel configurable.
- No se loguean contraseñas, tokens ni datos sensibles.
- Actuator expone únicamente `/actuator/health`, público para Docker/CI; el resto de endpoints sigue exigiendo JWT.
- El `HEALTHCHECK` del contenedor consulta `/actuator/health`.
- Alertas básicas de errores 5xx (pendiente).

---

## 11. Estado actual y roadmap

### 11.1. Estado actual (tras M11)

| Módulo | Estado | Nota |
|---|---|---|
| M0 Fundaciones | ✅ | Docker, README, errores uniformes, Swagger, CORS, auditoría y CI |
| M1 Identidad y acceso | ✅ | JWT, RBAC, cuentas por rol y administración de usuarios |
| M2 Estructura académica | ✅ | `AnioEscolar`, `Bimestre`, `Seccion`, `Curso` y `HorarioBloque` |
| M3 Alumnos y matrícula | ✅ | Matrícula anual, apoderados, cambio de sección y retiro/reingreso |
| M4 Planificación | ✅ | Unidades y contenidos con publicación y orden |
| M5 Asistencia | ✅ | Asistencia por bloque/fecha, tardanza y resúmenes |
| M6 Calificaciones | ✅ | Notas por criterio, promedios y acta |
| M7 Justificaciones | ✅ | Tickets de apoderado y resolución de Secretaría |
| M8 Materiales y Storage | ✅ | Buckets privados, materiales y sustento de tickets |
| M9 Comunicados | ✅ | Generales y por sección, autoría, visibilidad y baja lógica |
| M10 Cierre y reportes | ✅ | Cierre anual, boletas, historial y reportes |
| M11 Endurecimiento y Flyway | ✅ | Flyway, `ddl-auto=validate`, Docker non-root, health check y CI |

### 11.2. Orden de implementación

1. M0: README, reset, revisión de fundaciones.
2. M1: adaptar identidad y perfiles al modelo v3.
3. M2: año + bimestre + secciones + cursos + horario.
4. M3: matrícula anual con `seccion_id`.
5. M4: unidades y contenidos.
6. M5: asistencia por bloque y fecha.
7. M6: calificaciones por criterio.
8. M7: tickets.
9. M8: materiales y Storage.
10. M9: comunicados.
11. M10: cierre y reportes.
12. M11: endurecimiento y Flyway.

### 11.3. Definición de “módulo terminado”

Un módulo se considera terminado cuando:
- El código compila y los tests pasan.
- Las reglas de este documento están implementadas.
- Swagger está actualizado.
- Hay tests de éxito y de error.
- `docs/CONTEXT.md` se actualizó si cambió alguna decisión.
- El frontend tiene el contrato estable o se documentó el cambio.

---

## 12. Decisiones, supuestos y limitaciones

### 12.1. Decisiones cerradas

| # | Decisión |
|---|---|
| 1 | `PeriodoAcademico` se reemplaza por `AnioEscolar` (tabla `anios_escolares`). |
| 2 | La matrícula es anual y lleva `seccion_id`. No hay `MatriculaSeccion`. La sección puede cambiarse solo antes de `AnioEscolar.fechaInicio` (primer día de clases) y sin actividad registrada. |
| 3 | No hay `SesionClase`: la asistencia se registra por `(bloque_id, fecha, matricula_id)`. |
| 4 | No hay `Evaluacion`: la calificación usa `criterio` libre y promedio simple. |
| 5 | No hay módulo de pagos, pensiones ni cuotas. |
| 6 | No hay reprogramaciones de clases. |
| 7 | Nota aprobatoria `11.00`; asistencia mínima `70%`. `JUSTIFICADA` cuenta como asistencia efectiva. |
| 8 | `PLANIFICADO` existe para año y bimestre, y es editable. |
| 9 | `Comunicado` es un mensaje simple: autor, año, contenido, sección opcional y fecha/hora. |
| 10 | Flyway se implementa al final (M11); durante el desarrollo se recrea la BD. |
| 11 | Un curso tiene un solo docente. |
| 12 | Los promedios se calculan; no hay actas inmutables. |
| 13 | `SECRETARIA` publica solo comunicados generales; `DOCENTE` solo en sus secciones. |
| 14 | Para aprobar el grado, todos los cursos deben tener promedio anual ≥ `11.00` y la asistencia global debe ser ≥ `70%`. |
| 15 | M4: el `numero` de `Unidad` es automático dentro de `(curso, bimestre)`; los contenidos se devuelven ordenados y se reordenan en bulk. El `PUT` de unidad solo edita título, descripción y fechas. Se puede publicar sin contenidos. `ADMIN` lee todo, incluidos borradores, sin editar. |
| 16 | M5: el docente solo envía `asistio`; el sistema deriva `TARDANZA` si supera 15 minutos desde `HorarioBloque.horaInicio`. Las fechas pasadas sin registro se asumen `PRESENTE` al consultar. |
| 17 | M5: se rechaza asistencia futura con `400`; el bulk es upsert parcial; el GET de clase devuelve todos los alumnos activos con `estado=null` si no hay registro. |
| 18 | M5: `JUSTIFICADA` no se puede registrar desde la API docente; solo `SECRETARIA` la produce al aprobar un ticket (M7). |
| 19 | M5: los resúmenes incluyen totales, asistencia efectiva, faltas y porcentaje. |
| 20 | Con asistencia o notas registradas no se puede cambiar la sección (`409`), borrar un curso (`409`) ni despublicar/borrar una unidad con notas (`409`). |
| 21 | M6: `Calificacion` es única por `(matricula_id, unidad_id, criterio)`; `criterio` es texto libre de hasta 120 caracteres con `trim`; `nota` es `BigDecimal(4,2)` entre `0.00` y `20.00`. |
| 22 | M6: solo se califica con el bimestre `ABIERTO` y el año no `CERRADO`; más de dos decimales o fuera de rango devuelve `400`. |
| 23 | M6: el `PUT` es upsert parcial por `(matriculaId, criterio)`; un duplicado `(matrícula, criterio)` en la misma request devuelve `400`. |
| 24 | M6: el borrado es físico y solo con bimestre `ABIERTO`; en `CERRADO` se corrigen notas, no se borran. |
| 25 | M6: alumno y apoderado solo ven notas de unidades `PUBLICADA`; docente, secretaría y admin ven también borradores. |
| 26 | M6: promedio de unidad = media simple; de bimestre por curso = media de unidades con notas; anual por curso = media de bimestres con notas; general = media de los promedios anuales de cada curso. `HALF_UP` a 2 decimales; `null` sin notas. |
| 27 | M6: el acta tiene una fila por alumno, una columna por unidad y el promedio del bimestre. |
| 28 | M7: el sustento es texto libre opcional en M7; M8 lo reemplaza por `storagePath` y upload multipart. |
| 29 | M7: un ticket con bloque exige que la fecha caiga en el día de ese bloque. |
| 30 | M7: `ADMIN` no accede a la bandeja ni al detalle de tickets; solo `SECRETARIA` resuelve y lee. |
| 31 | M7: el listado de tickets del apoderado acepta filtros opcionales `anioId` y `estado`. |
| 32 | M7: aprobar convierte solo filas `FALTA` a `JUSTIFICADA` y las vincula al ticket; `PRESENTE` y `TARDANZA` no se tocan. |
| 33 | M7: si la asistencia se registra después de aprobar, nace como `JUSTIFICADA` y vinculada al ticket. |
| 34 | M7: solo un ticket activo (`PENDIENTE`/`APROBADO`) por alumno, fecha y bloque; el alcance de día completo prevalece; `RECHAZADO` no bloquea uno nuevo. |
| 35 | M7: los tickets no se eliminan; los estados son la historia. |
| 36 | M8: el material nace `publicado=false` y se publica con `PATCH`; `ADMIN` lee todo, incluidos borradores, sin modificar. La descarga usa URL firmada de 5 minutos. La baja es lógica y elimina el objeto del bucket. |
| 37 | M8: el sustento se sube/reemplaza solo con el ticket `PENDIENTE`; el archivo vive en el bucket privado `sustentos` y se descarga con URL firmada. |
| 38 | M9: `ADMIN` gestiona cualquier comunicado; `SECRETARIA` y `DOCENTE` solo los propios. `SECRETARIA` publica solo generales; `DOCENTE` solo en secciones donde dicta clase o es tutor. Contenido máximo 5000 caracteres; año `CERRADO` es solo lectura (`409`); baja lógica (`activo=false`). |
| 39 | M10: el cierre exige al menos un bimestre y todos `CERRADO`; solo cuentan notas de unidades `PUBLICADA`. `APROBADO` exige todos los cursos con promedio anual ≥ `11.00` y asistencia global ≥ `70%`; sin registros de asistencia el porcentaje es `0.00%`. `RETIRADO` se conserva y no bloquea. Al aprobar 5° grado, el alumno pasa a `EGRESADO`. El cierre es idempotente y los reportes validan que bimestre y sección pertenezcan al año. |
| 40 | M11: Flyway gobierna el esquema y Hibernate valida (`ddl-auto=validate`). Los datos demo viven en `db/seed` y solo se cargan si `FLYWAY_LOCATIONS` incluye esa ruta; en producción se usa solo `classpath:db/migration`. Las migraciones aplicadas no se editan. |

### 12.2. Supuestos operativos

- La asistencia efectiva es `PRESENTE + TARDANZA + JUSTIFICADA`; solo `FALTA` es inasistencia injustificada.
- El porcentaje de asistencia se calcula sobre asistencias registradas, no sobre días calendario.
- Si falta la nota de algún curso, el cierre se bloquea y requiere revisión de `ADMIN`.
- El cierre de año es una acción manual de `ADMIN`, no automática por fecha.

### 12.3. Limitaciones conocidas

1. No hay reprogramaciones.
2. No hay cambios de sección una vez iniciado el año.
3. No hay pagos.
4. No hay actas inmutables.
5. No hay entidad `Aula`.
6. No hay primaria ni inicial.
7. No hay notificaciones push/correo.
8. El rate limiting de login es en memoria; no sirve con múltiples instancias.
9. No hay refresh tokens: el JWT dura 24 h y debe renovarse iniciando sesión.
10. No hay backups automáticos ni métricas/alertas; el health check es básico.

---

## 13. Registro de cambios

### v3.5 — 2026-11-04 (M11 implementado)

- M11 Endurecimiento queda ✅: Flyway gobierna el esquema y Hibernate valida con `ddl-auto=validate`.
- `V1__esquema_inicial.sql` reproduce el esquema sobre PostgreSQL 17; `V2__datos_demo.sql` crea los cinco actores y un escenario mínimo.
- `FLYWAY_LOCATIONS` separa esquema y datos demo; en producción se usa solo `classpath:db/migration`.
- El perfil `test` carga solo el esquema; la suite completa sigue en 252 tests.
- Docker corre como usuario non-root y tiene `HEALTHCHECK` contra `/actuator/health`; Actuator expone únicamente `health` y lo deja público para Docker/CI.
- Verificado con la imagen real: arranca como `educa360`, aplica V1+V2 y queda `healthy`; el login demo y los endpoints protegidos responden `200`/`401` según corresponda.
- Las credenciales demo se documentan en `docs/CREDENCIALES.md`.
- Refresh tokens, rate limiting distribuido, backups y métricas quedan documentados como limitaciones.

### v3.4 — 2026-11-04 (M10 implementado)

- M10 Cierre y reportes queda ✅: `CierreService` calcula el cierre anual, las boletas, el historial y los reportes.
- El cierre exige todos los bimestres `CERRADO` y bloquea si a un alumno activo le falta el promedio anual de algún curso.
- `APROBADO` exige todos los cursos ≥ `11.00` y asistencia global ≥ `70%`; un curso desaprobado o asistencia baja deja `DESAPROBADO`.
- Los cálculos usan solo unidades `PUBLICADA`; `RETIRADO` se conserva y no bloquea; aprobar 5° deja `EGRESADO`.
- Se agregan `GET .../cierre/preview`, `POST .../cerrar`, boleta de alumno/apoderado, historial de Secretaría y reportes de asistencia/calificaciones.
- El cierre es idempotente; los reportes exigen `anioId` y validan bimestre/sección (`409`).
- Tests: `CierreServiceTest` (15) y `CierreApiTest` (3); suite total 252.

### v3.3 — 2026-11-04 (M9 implementado)

- M9 Comunicados queda ✅: entidad `Comunicado` con autor, año, sección opcional, contenido y baja lógica.
- `ADMIN` publica generales o por sección y gestiona cualquiera; `SECRETARIA` solo generales propios; `DOCENTE` solo secciones donde dicta clase o es tutor, y solo los suyos.
- Alumno y apoderado leen generales y los de su sección (el apoderado, los de sus hijos activos con matrícula vigente).
- Se agregan `PUT`/`DELETE` para Secretaría y Docente, filtros de listado y la regla de año `CERRADO` como solo lectura (`409`).
- Contenido obligatorio con `trim` y máximo de 5000 caracteres.
- Tests: `ComunicadoServiceTest` (20) y `ComunicadoApiTest` (8); suite total 234.

### v3.2 — 2026-11-04 (M8 implementado)

- M8 Materiales y Storage queda ✅: entidad `MaterialClase`, buckets privados `materiales` y `sustentos`, URLs firmadas de 5 minutos y validación de archivos.
- `TicketJustificacion.urlArchivoSustento` se reemplaza por `storagePath`; el sustento se sube aparte con multipart y solo mientras el ticket está `PENDIENTE`.
- Se documentan las rutas reales sin prefijo de bucket (`{cursoId}/...`, `{ticketId}/...`) y los límites de 25 MB / 5 MB.
- Se completan los guards de borrado: curso, unidad o contenido con materiales responden `409`.

### v3.1 — 2026-11-04 (auditoría tras M7)

- Se actualizan a ✅ los módulos M0–M7 y las entidades implementadas.
- Se reemplaza “Estado al iniciar la v3” por el estado actual tras M7.
- Se cierran en §12.1 las decisiones de M4, M5, M6, M7 y M8.
- Se documenta que el alcance día completo vs. bloque de tickets se valida en servicio, porque el índice parcial solo cubre el mismo bloque.
- Se resuelven los cabos sueltos de M8: `urlArchivoSustento` → `storagePath`, upload/descarga de sustento, publicación/despublicación de materiales, lectura de `ADMIN`, límites de archivo y rutas de Storage.
- Se corrige la matriz de autorización: `ADMIN` no accede a tickets.

### v3 — 2026-11-02

- Se simplifica el modelo para un proyecto universitario.
- Se elimina `MatriculaSeccion`; `Matricula` es anual con `seccion_id`.
- Se elimina `SesionClase`; la asistencia cuelga de `(bloque_id, fecha, matricula_id)`.
- Se elimina `Evaluacion`; `Calificacion` usa `criterio` libre y promedio simple.
- Se elimina el estado de pago de `Matricula`.
- Se simplifica `Comunicado` a un mensaje con autor, contenido, fecha/hora y sección opcional; sin título.
- `SECRETARIA` puede publicar comunicados generales; `DOCENTE` solo en sus secciones.
- Se documenta que no hay reprogramaciones y que la sección solo se cambia antes de iniciar clases.
- Para aprobar el grado se exige aprobar todos los cursos y cumplir la asistencia global.
- `JUSTIFICADA` cuenta como asistencia efectiva.
- Flyway se difiere a M11; durante el desarrollo se recrea la BD.
- Se mantiene `PLANIFICADO` como estado editable.

### v2 — 2026-11-02

- Propuesta intermedia con un modelo más granular (matrícula por sección, sesiones de clase y evaluaciones con peso).
- Reemplazada por la v3 al simplificar el alcance para un proyecto universitario.
- Se mantuvo el modelo anual `AnioEscolar` + `Bimestre` y el resto de decisiones de fondo.

### v1 — anterior

- Modelo inicial con `PeriodoAcademico` como bimestre.
- Módulos 1–3 implementados.
- 14 entidades y 79 tests.
- Docker Compose y CI.
