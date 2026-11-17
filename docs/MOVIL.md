# Guía de integración móvil

Contrato que consume la app (Android/iOS). Es un resumen operativo: el detalle
de reglas vive en [`CONTEXT.md`](./CONTEXT.md). Si algo no aparece aquí, no se
inventa: se pide y se documenta.

- **Base:** `https://<backend>/api/v1`
- **Auth:** JWT en `Authorization: Bearer <token>`. Dura 24 h; al expirar hay que
  volver a hacer login (no hay refresh token).
- **Formato:** JSON `camelCase`, fechas `YYYY-MM-DD`, horas `HH:mm:ss`.
- **Errores:** cuerpo uniforme `ApiError` (`status`, `error`, `message`, `path`,
  `fields`). `fields` solo viene en errores de validación (`400`).

---

## 1. Sesión

| Método y ruta | Rol | Descripción |
|---|---|---|
| `POST /auth/login` | público | Devuelve `token`, `tokenType`, `expiresIn` (segundos) y `user` |
| `POST /auth/register` | público (solo BD vacía) | Bootstrap del primer ADMIN |
| `GET /auth/me` | autenticado | `usuario` + `perfil` del rol |
| `PATCH /auth/me/telefonos` | autenticado (excepto ESTUDIANTE) | Reemplaza **mis** teléfonos |

> Ojo con el nombre del campo: el login devuelve `user` y `/auth/me` devuelve
> `usuario` (misma forma de `UserResponse`). Es el contrato actual; no se cambia
> para no romper clientes.

### `GET /auth/me`

```json
{
  "usuario": {
    "id": 7, "email": "dana@educa360.pe", "nombres": "Dana", "apellidos": "Perez",
    "dni": "20000001", "rol": "DOCENTE", "activo": true,
    "telefonos": ["987654321"], "createdAt": "2026-03-01T10:00:00"
  },
  "perfil": { "titulo": "Lic. en Matemáticas", "materias": [ ... ] }
}
```

`perfil` cambia por rol: `DOCENTE` trae título y materias; `ESTUDIANTE`, la
sección del período vigente; `APODERADO`, sus hijos; los demás, `null`.

### `PATCH /auth/me/telefonos`

La lista enviada **es la que queda** (reemplazo, no agregar/quitar):

```json
{ "telefonos": ["987654321", "+51999888777"] }
```

- `[]` quita todos los números.
- No se acepta `null` (`400`).
- Máximo 5 números, sin duplicados, formato `\+?\d{7,15}`.
- `ESTUDIANTE` responde `403`: al ser menor, la comunicación es con su apoderado.
- Devuelve el mismo `MeResponse` que `GET /auth/me`, para refrescar la pantalla
  sin una segunda llamada.

---

## 2. Cursos y `cantidadAlumnos`

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /docente/cursos` | DOCENTE | Mis cursos |
| `GET /estudiante/cursos` | ESTUDIANTE | Cursos de mi sección vigente |

Ambos devuelven `CursoResponse`:

```json
{
  "id": 5, "docenteId": 2, "docenteNombre": "Dana Perez",
  "materiaId": 1, "materiaNombre": "Matemática",
  "seccionId": 3, "seccionEtiqueta": "3°A",
  "anioId": 1, "anioNombre": "Año Escolar 2026",
  "cantidadAlumnos": 28,
  "createdAt": "2026-03-01T10:00:00"
}
```

`cantidadAlumnos` es el tamaño **vigente** de la sección: alumnos `ACTIVO` con
matrícula no `RETIRADA`. Sirve para pintar la cabecera de la clase sin abrir la
hoja. No incluye retirados ni trasladados.

---

## 3. Horarios

El horario es semanal y recurrente: un curso se repite varios días. La
asistencia cuelga de `(bloqueId, fecha)`.

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /docente/horario` | DOCENTE | Mi semana completa (todos mis cursos) |
| `GET /docente/cursos/{cursoId}/horario` | DOCENTE | Bloques de un curso propio |
| `GET /estudiante/horario` | ESTUDIANTE | Horario de mi sección vigente |
| `GET /apoderado/hijos/{hijoId}/horario` | APODERADO | Horario del hijo vinculado |

`GET /docente/horario`, `/estudiante/horario` y `/apoderado/hijos/{id}/horario`
devuelven la **misma** forma, ya ordenada por día (`LUNES`→`SÁBADO`) y hora:

```json
[
  {
    "bloqueId": 12, "cursoId": 5,
    "materiaId": 1, "materiaNombre": "Matemática",
    "docenteId": 2, "docenteNombre": "Dana Perez",
    "dia": "LUNES", "horaInicio": "08:00:00", "horaFin": "09:00:00"
  }
]
```

- Sin matrícula (o sin año abierto) devuelve `[]`, no es error.
- `dia` solo llega de `LUNES` a `SABADO`; no hay bloques en domingo.

---

## 4. Hijos del apoderado y `anioId`

`GET /apoderado/hijos` devuelve `HijoResponse`:

```json
[
  {
    "estudianteId": 9, "nombres": "Alma", "apellidos": "Luna",
    "dni": "30000001", "email": "alma@educa360.pe",
    "seccionEtiqueta": "3°A", "anioId": 1, "anioNombre": "Año Escolar 2026",
    "situacionFinal": "EN_CURSO", "parentesco": "MADRE",
    "responsablePrincipal": true
  }
]
```

- `anioId` es el id del **año vigente**; es el que se pasa a los filtros
  `anioId` de asistencias y calificaciones.
- `seccionEtiqueta` y `anioId` llegan `null` si el hijo aún no está matriculado
  (el vínculo existe antes de la matrícula).

---

## 5. Asistencia del docente

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /docente/asistencias/resumen?fecha=` | DOCENTE | Avance del día, todos mis bloques |
| `GET /docente/cursos/{cursoId}/asistencia?bloqueId=&fecha=` | DOCENTE | Hoja de una clase |
| `PUT /docente/cursos/{cursoId}/asistencia` | DOCENTE | Upsert parcial de la hoja |

### Resumen del día (una sola llamada)

```json
{
  "fecha": "2026-11-16",
  "dia": "LUNES",
  "totalBloques": 2,
  "bloquesCompletos": 1,
  "bloques": [
    {
      "bloqueId": 12, "cursoId": 5, "materiaNombre": "Matemática",
      "seccionEtiqueta": "3°A", "horaInicio": "08:00:00", "horaFin": "09:00:00",
      "totalAlumnos": 28, "registradas": 28, "pendientes": 0,
      "presentes": 25, "tardanzas": 1, "justificadas": 1, "faltas": 1
    }
  ]
}
```

- `pendientes = totalAlumnos - registradas` (nunca negativo). Un bloque está
  completo cuando `pendientes == 0`; `bloquesCompletos` los cuenta.
- Solo aparecen bloques del año calendario de `fecha` (el horario de años
  anteriores no se mezcla).
- Domingo: `dia: null`, `totalBloques: 0`, `bloques: []`.
- Si el docente no dicta ese día, `bloques: []`.

### Hoja de clase

El `PUT` solo lleva los alumnos que se marcan o corrigen; el resto conserva su
estado. El docente envía `asistio` (boolean), **no** el estado: el backend
deriva `PRESENTE`, `FALTA` o `TARDANZA` (más de 15 min del inicio del bloque).
`JUSTIFICADA` solo nace de un ticket aprobado por Secretaría.

```json
{
  "bloqueId": 12,
  "fecha": "2026-11-16",
  "asistencias": [
    { "matriculaId": 21, "asistio": true },
    { "matriculaId": 22, "asistio": false, "observacion": "Sin aviso" }
  ]
}
```

---

## 6. Notificaciones y push

| Método y ruta | Rol | Descripción |
|---|---|---|
| `GET /notificaciones?leida=` | autenticado | Mis notificaciones |
| `GET /notificaciones/contador` | autenticado | `{"noLeidas": n}` |
| `PATCH /notificaciones/{id}/leida` | autenticado | Marcar una (idempotente) |
| `PATCH /notificaciones/leidas` | autenticado | Marcar todas |
| `POST /notificaciones/dispositivos` | autenticado | Registrar/actualizar token |
| `GET /notificaciones/dispositivos` | autenticado | Mis dispositivos |
| `DELETE /notificaciones/dispositivos?token=` | autenticado | Borrar token al cerrar sesión |

### Registro del token

- Se obtiene con `expo-notifications` y se envía en `POST` tras el login.
- El backend hace upsert por token: el mismo dispositivo no duplica filas y, si
  otra cuenta inicia sesión en él, el token se reasigna a esa cuenta.
- En logout: `DELETE /notificaciones/dispositivos?token=<token>` (idempotente).
- Las credenciales de FCM/APNs viven en **EAS**, no en el backend.
- Con `APP_PUSH_ENABLED=false` el token se puede registrar, pero no se envían
  pushes; las notificaciones siguen en la bandeja y por WebSocket.

### `referenciaId`: qué abrir al tocar el aviso

`tipo` dice de qué trata; `referenciaId` identifica el registro concreto.

| `tipo` | `referenciaId` apunta a | Pantalla sugerida |
|---|---|---|
| `COMUNICADO` | `Comunicado.id` | Detalle del comunicado |
| `TICKET_RESUELTO` | `TicketJustificacion.id` | Detalle del ticket |
| `CALIFICACION` | `Calificacion.id` | Detalle de la calificación en su unidad |
| `ASISTENCIA` | `Asistencia.id` | Detalle de la asistencia del día |
| `MATERIAL` | `MaterialClase.id` | Detalle del material |
| `CIERRE` | `AnioEscolar.id` | Boleta / cierre del año |

- Si `referenciaId` es `null`, abrir la bandeja.
- Un `CALIFICACION` eliminado conserva el id, pero el detalle responde `404`:
  en ese caso, volver a la bandeja.

### Tiempo real (opcional)

- STOMP nativo en `/ws`; fallback SockJS en `/ws-sockjs`.
- El JWT va en el header `Authorization` del frame `CONNECT`.
- Suscripción: `/user/queue/notificaciones`.
- Si el usuario está desconectado, al volver a abrir la app consulta la bandeja:
  la notificación ya está persistida.

---

## 7. Fuera de alcance (decidido con el equipo)

- **Calendario escolar / días no lectivos:** el backend no modela feriados y no
  bloquea el registro de asistencia en esas fechas. La app no debe prometer ese
  bloqueo.
- **Comunicado a varias secciones:** no se implementa. `DOCENTE` publica en una
  sola sección donde dicta clase o es tutor. Un aviso a varias secciones lo
  publica `SECRETARIA` o `ADMIN` como **general**, que llega a todas.

Si alguno de estos dos se vuelve requisito, se diseña y se documenta antes de
tocar la app.
