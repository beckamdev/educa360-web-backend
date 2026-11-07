# Despliegue en producción — Railway + Supabase

Guía para publicar el backend. La plantilla de variables está en
[`.env.production.example`](../.env.production.example); los valores reales
**nunca** se versionan.

## Arquitectura

| Pieza | Dónde |
|---|---|
| Frontend | Vercel |
| Backend (Spring Boot) | Railway, con el `Dockerfile` del repo |
| PostgreSQL | Supabase (Session Pooler) |
| Archivos (`materiales`, `sustentos`) | Supabase Storage (buckets privados) |

Railway **no** lee `.env`, `docker-compose.yml` ni `.env.production.example`.
Solo lee las variables del panel del servicio. Tampoco necesita un PostgreSQL
propio ni volúmenes: la aplicación es stateless.

## 1. Supabase

### 1.1 Base de datos

1. Crea el proyecto en Supabase.
2. Ve a **Project Settings → Database → Connection string** y elige
   **Session pooler** (puerto `5432`).
3. Traduce la cadena a JDBC:

   ```text
   postgresql://postgres.<project-ref>:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres
   ```

   ```text
   jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
   ```

4. La contraseña va aparte, en `SPRING_DATASOURCE_PASSWORD`. Si Supabase la
   muestra URL-encoded (`%2B`, `%3D`), decodifícala antes de pegarla.
5. Si olvidaste la contraseña: **Project Settings → Database → Reset database
   password**. No borra datos; actualiza la variable en Railway después.

> No uses el Transaction Pooler (puerto `6543`): Flyway falla con él.

### 1.2 Storage

1. Ve a **Storage → New bucket**.
2. Crea dos buckets **privados**:
   - `materiales`
   - `sustentos`
3. No hacen falta políticas RLS: el backend usa la `service_role`, que las omite.
4. Copia la clave `service_role` desde **Project Settings → API Keys**
   (si solo ves las nuevas `sb_secret_...`, abre **Legacy API keys**).
   El backend la envía como `Authorization: Bearer <key>`.

La `service_role` key **solo** va en el backend. Nunca en el frontend.

## 2. Railway

1. Entra a [railway.app](https://railway.app) con GitHub.
2. **New Project → Deploy from GitHub repo**.
3. Autoriza el repositorio `educa360-web-backend` (si no aparece, revisa el
   acceso de la GitHub App en GitHub → Settings → Applications).
4. En **Settings → Source**, selecciona la rama **`main`**.
5. Railway detecta el `Dockerfile` y el `railway.toml` automáticamente.
6. **No** agregues un PostgreSQL de Railway ni volúmenes.
7. En **Variables → Raw Editor**, pega las variables de
   [`.env.production.example`](../.env.production.example) con los valores
   reales.
8. **Deploy / Redeploy**.
9. En **Settings → Networking → Generate Domain** obtienes el dominio público.

Cada push a `main` reconstruye y redeploya solo.

## 3. Verificación

```bash
curl https://<tu-dominio>/actuator/health
# {"status":"UP"}
```

En **Deployments → Logs** deberías ver Flyway aplicando `V1` y `V3` y luego
`Started BackendApplication`.

| Recurso | URL |
|---|---|
| API | `https://<tu-dominio>` |
| Swagger UI | `https://<tu-dominio>/swagger-ui/index.html` |
| OpenAPI JSON | `https://<tu-dominio>/v3/api-docs` |
| WebSocket STOMP | `wss://<tu-dominio>/ws` |
| WebSocket SockJS | `https://<tu-dominio>/ws-sockjs` |

## 4. Primer usuario

Con la base vacía (producción no carga el seed), el primer registro crea el
`ADMIN`:

```bash
curl -X POST https://<tu-dominio>/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@colegio.pe","password":"Admin12345","nombres":"Admin","apellidos":"General","dni":"12345678"}'
```

Después de esa cuenta, `/auth/register` responde `403` y las altas pasan por
`/api/v1/admin/*` y `/api/v1/secretaria/*`.

## 5. Notas operativas

- **Una sola instancia**: el broker STOMP es en memoria. Para escalar a varias
  réplicas hay que moverlo a Redis.
- **JWT**: lo emite y valida el backend con `APP_JWT_SECRET`. No se guarda en
  Supabase. Cambiar el secreto invalida todos los tokens emitidos.
- **CORS**: `*` funciona mientras el frontend no tenga dominio. Luego cámbialo
  al dominio real de Vercel.
- **Supabase free tier**: se pausa por inactividad y limita conexiones. Para
  producción real conviene un plan pago.
- **Secretos**: solo en Railway y en el gestor de contraseñas. Nunca en Git ni
  en el frontend.

## 6. Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| `password authentication failed` | Contraseña URL-encoded o usuario incorrecto (`postgres.<project-ref>`) |
| Flyway no conecta | Transaction Pooler (6543) en vez de Session Pooler (5432) o falta `sslmode=require` |
| El contenedor no arranca | Falta `APP_JWT_SECRET` o tiene menos de 32 bytes |
| Healthcheck en rojo | Variables `SPRING_DATASOURCE_*` ausentes: la app intenta `localhost` |
| Materiales/sustentos dan `503` | Falta `APP_SUPABASE_URL`, la `service_role` key o los buckets |
| WebSocket no conecta | Usar `ws://` en vez de `wss://`, o el header `Authorization` en el `CONNECT` |
