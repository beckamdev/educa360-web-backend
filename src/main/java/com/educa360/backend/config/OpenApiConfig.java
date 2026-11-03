package com.educa360.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Metadatos de la documentación OpenAPI y esquema de seguridad JWT.
 *
 * Con esto Swagger UI muestra el botón "Authorize" para pegar el token
 * emitido por {@code POST /api/v1/auth/login}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI educa360OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Educa360 API")
                        .description("""
                                API REST para la gestión académica del Colegio Nuestra Señora de Monserrat.

                                Autenticación: envía el token JWT en el header
                                `Authorization: Bearer <token>`.
                                """)
                        .version("v1"))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Entorno local")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
