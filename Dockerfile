# =========================
# Etapa 1: Build
# =========================
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copiamos Maven Wrapper
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

# Damos permisos de ejecución al Maven Wrapper
RUN chmod +x mvnw

# Descargamos las dependencias
RUN ./mvnw dependency:go-offline -B

# Copiamos el código fuente
COPY src ./src

# Compilamos el proyecto
RUN ./mvnw clean package -DskipTests


# =========================
# Etapa 2: Runtime
# =========================
FROM eclipse-temurin:25-jre

WORKDIR /app

# Contenedor non-root: la aplicación no corre como root.
RUN groupadd --system educa360 \
    && useradd --system --gid educa360 --no-create-home educa360 \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=educa360:educa360 /app/target/*.jar app.jar

USER educa360

EXPOSE 8080

# Health check contra Actuator (solo se expone /actuator/health).
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
