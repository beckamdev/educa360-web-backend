# Educa360 Backend

API REST para la gestión académica del Colegio Nuestra Señora de Monserrat.

El dominio y el roadmap viven en [`docs/CONTEXT.md`](docs/CONTEXT.md). Este README cubre
solo el arranque técnico del proyecto.

## Stack

| Pieza | Versión |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.1 |
| PostgreSQL | 17 |
| Maven | Wrapper incluido (`./mvnw`) |
| Swagger / OpenAPI | springdoc 3.1.0 |

## Requisitos

- **JDK 25** (`java -version` debe reportar 25).
- **Docker** con **Docker Compose**.
- No hace falta instalar Maven: se usa el wrapper.

Si tu `java` por defecto no es 25, exporta `JAVA_HOME` antes de los comandos
Maven. Ejemplo en Linux:

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
```

## Arranque rápido con Docker

```bash
cp .env.example .env
# Edita .env y define al menos POSTGRES_PASSWORD y APP_JWT_SECRET
docker compose up -d --build
```

La primera vez, Flyway crea el esquema y carga los datos demo. Las cuentas de
prueba están en [`docs/CREDENCIALES.md`](docs/CREDENCIALES.md).

Servicios:

| Servicio | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| WebSocket STOMP | `ws://localhost:8080/ws` |
| WebSocket SockJS | `http://localhost:8080/ws-sockjs` |
| PostgreSQL | `127.0.0.1:5432` (solo accesible desde la máquina) |

### Notificaciones en tiempo real

El cliente se conecta a `/ws` (o `/ws-sockjs` como fallback) y envía el JWT en
el header `Authorization` del frame STOMP `CONNECT`. Luego se suscribe a:

```text
/user/queue/notificaciones
```

Las notificaciones también quedan persistidas y se consultan por REST en
`/api/v1/notificaciones`. Ver el Módulo 12 en [`docs/CONTEXT.md`](docs/CONTEXT.md).

Comandos útiles:

```bash
docker compose logs -f backend   # logs del backend
docker compose ps                # estado de los servicios
docker compose down              # detiene los contenedores (conserva la BD)
```

## Primer usuario (base vacía)

Con la base de datos vacía (por ejemplo, en producción sin el seed), el primer
registro crea la cuenta `ADMIN`:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@educa360.pe","password":"Admin12345","nombres":"Ana","apellidos":"Ruiz","dni":"10000001"}'
```

`telefonos` es opcional. En cuanto existe una cuenta, `/auth/register` responde
`403`: las altas posteriores las hace un `ADMIN` desde `/api/v1/admin/usuarios`
y, para alumnos y familias, desde `/api/v1/secretaria/...`.

## Variables de entorno

Todas las variables están documentadas en [`.env.example`](.env.example).
El archivo `.env` **no se versiona**.

| Variable | Obligatoria | Descripción |
|---|---|---|
| `POSTGRES_DB` | Sí | Nombre de la base de datos |
| `POSTGRES_USER` | Sí | Usuario de PostgreSQL |
| `POSTGRES_PASSWORD` | Sí | Contraseña de PostgreSQL |
| `POSTGRES_PORT` | No | Puerto local de PostgreSQL (por defecto `5432`) |
| `BACKEND_PORT` | No | Puerto local del backend (por defecto `8080`) |
| `FLYWAY_LOCATIONS` | No | Migraciones a aplicar. Desarrollo: esquema + datos demo; producción: solo `classpath:db/migration` |
| `APP_JWT_SECRET` | Sí | Clave HS256, mínimo 32 bytes |
| `APP_JWT_EXPIRATION_MS` | No | Validez del token (por defecto 24 h) |
| `APP_CORS_ALLOWED_ORIGINS` | No | Orígenes permitidos, separados por coma (`*` en desarrollo) |
| `APP_SECURITY_LOGIN_MAX_ATTEMPTS` | No | Intentos fallidos antes del bloqueo (por defecto `5`) |
| `APP_SECURITY_LOGIN_WINDOW_MINUTES` | No | Ventana de conteo en minutos (por defecto `15`) |
| `APP_SECURITY_LOGIN_COOLDOWN_MINUTES` | No | Duración del bloqueo en minutos (por defecto `15`) |
| `APP_SUPABASE_URL` | Para archivos | URL del proyecto Supabase (`https://<proyecto>.supabase.co`) |
| `APP_SUPABASE_SERVICE_ROLE_KEY` | Para archivos | Service role key; **solo backend**, nunca en el frontend |

Para materiales y sustentos, crea en Supabase los buckets **privados**
`materiales` y `sustentos`. Sin `APP_SUPABASE_URL` y
`APP_SUPABASE_SERVICE_ROLE_KEY`, esos endpoints responden `503`; el resto de la
API funciona normal.

Genera el secreto JWT con:

```bash
openssl rand -base64 48
```

## Reset de la base de datos (desarrollo)

El esquema lo gobierna Flyway. Para recrear todo desde cero:

```bash
docker compose down -v          # -v elimina el volumen de PostgreSQL
docker compose up -d --build
```

Al arrancar de nuevo, Flyway aplica `V1__esquema_inicial.sql`,
`V3__notificaciones.sql` y, en desarrollo, `V2__datos_demo.sql`.

## Ejecución local del backend

Con PostgreSQL levantado en Docker:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

La conexión por defecto es `jdbc:postgresql://localhost:5432/educa360` con
usuario y contraseña `postgres`. Puedes sobreescribirla con
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y
`SPRING_DATASOURCE_PASSWORD`.

## Tests

```bash
./mvnw test
```

Los tests de integración usan **Testcontainers** con PostgreSQL 17. Si Docker no
está disponible, esos tests se omiten (`disabledWithoutDocker = true`) y los
tests unitarios siguen ejecutándose.

## Estructura

```text
src/main/java/com/educa360/backend/
├── config/      # OpenAPI, WebSocket y configuración transversal
├── controller/  # endpoints REST
├── dto/         # request/response
├── entity/      # entidades JPA (todas extienden Auditable)
├── exception/   # ApiError y GlobalExceptionHandler
├── repository/  # Spring Data JPA
├── security/    # JWT, RBAC y CORS
└── service/     # reglas de negocio transaccionales
```

## Errores

Todas las respuestas de error usan el cuerpo `ApiError`:

```json
{
  "timestamp": "2026-10-14T10:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "El email ya está registrado",
  "path": "/api/v1/auth/register",
  "fields": null
}
```

En errores de validación (`400`), `fields` trae el detalle por campo.

| Código | Cuándo |
|---|---|
| `400` | Validación del request |
| `401` | Falta el token o es inválido |
| `403` | Autenticado sin permiso para la zona |
| `404` | Recurso inexistente |
| `409` | Conflicto con una regla de negocio |
| `413` | Archivo supera el tamaño permitido |
| `429` | Login bloqueado por intentos fallidos (incluye `Retry-After`) |
| `503` | Storage no configurado o no disponible |

## Convenciones de API

- Prefijo: `/api/v1`.
- Autenticación: `Authorization: Bearer <token>`.
- Errores uniformes con el cuerpo `ApiError`.
- Fechas/hora en ISO-8601.

El detalle de módulos, endpoints y reglas de negocio está en
[`docs/CONTEXT.md`](docs/CONTEXT.md).
