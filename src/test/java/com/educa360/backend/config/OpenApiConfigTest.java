package com.educa360.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    private final OpenAPI openApi = new OpenApiConfig().educa360OpenApi();

    @Test
    void exponeLosMetadatosDeLaApi() {
        assertEquals("Educa360 API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
        assertEquals(1, openApi.getServers().size());
    }

    @Test
    void defineElEsquemaBearerJwt() {
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");

        assertNotNull(scheme);
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
    }

    @Test
    void exigeElEsquemaBearerPorDefecto() {
        assertTrue(openApi.getSecurity().stream()
                .anyMatch(requirement -> requirement.containsKey("bearerAuth")));
    }
}
