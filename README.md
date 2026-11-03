# Educa360 Backend

API REST para la gestión académica del Colegio Nuestra Señora de Monserrat.

El dominio y el roadmap viven en [`CONTEXT.md`](CONTEXT.md). Este README cubre
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

Servicios:

| Servicio | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| PostgreSQL | `127.0.0.1:5432` (solo accesible desde la máquina) |

Comandos útiles:

```bash
docker compose logs -f backend   # logs del backend
docker compose ps                # estado de los servicios
docker compose down              # detiene los contenedores (conserva la BD)
```

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
| `APP_JWT_SECRET` | Sí | Clave HS256, mínimo 32 bytes |
| `APP_JWT_EXPIRATION_MS` | No | Validez del token (por defecto 24 h) |
| `APP_CORS_ALLOWED_ORIGINS` | No | Orígenes permitidos, separados por coma (`*` en desarrollo) |
| `APP_SECURITY_LOGIN_MAX_ATTEMPTS` | No | Intentos fallidos antes del bloqueo (por defecto `5`) |
| `APP_SECURITY_LOGIN_WINDOW_MINUTES` | No | Ventana de conteo en minutos (por defecto `15`) |
| `APP_SECURITY_LOGIN_COOLDOWN_MINUTES` | No | Duración del bloqueo en minutos (por defecto `15`) |

Genera el secreto JWT con:

```bash
openssl rand -base64 48
```

## Reset de la base de datos (desarrollo)

Durante el desarrollo el esquema se genera con `ddl-auto=update`; todavía no hay
migraciones Flyway (se incorporan en el módulo M11). Para recrear todo desde
cero:

```bash
docker compose down -v          # -v elimina el volumen de PostgreSQL
docker compose up -d --build
```

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
├── config/      # OpenAPI y configuración transversal
├── controller/  # endpoints REST
├── dto/         # request/response
├── entity/      # entidades JPA (todas extienden Auditable)
├── exception/   # ApiError y GlobalExceptionHandler
├── repository/  # Spring Data JPA
├── security/    # JWT, RBAC y CORS
└── service/     # reglas de negocio transaccionales
```

## Convenciones de API

- Prefijo: `/api/v1`.
- Autenticación: `Authorization: Bearer <token>`.
- Errores uniformes con el cuerpo `ApiError`.
- Fechas/hora en ISO-8601.

El detalle de módulos, endpoints y reglas de negocio está en
[`CONTEXT.md`](CONTEXT.md).
