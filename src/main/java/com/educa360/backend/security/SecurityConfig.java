package com.educa360.backend.security;

import com.educa360.backend.exception.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Configuración central de la seguridad:
 *
 * - Stateless (sin sesiones): la identidad viaja en el JWT.
 * - Público: login, alta inicial de la primera cuenta y Swagger.
 * - Todo lo demás: requiere token válido.
 * - Los errores 401/403 se responden en JSON, no en HTML.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // activa @PreAuthorize en los controladores
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    // Boot 4 / Spring Framework 7: el ObjectMapper auto-configurado es Jackson 3 (tools.jackson)
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper,
            @Value("${app.cors.allowed-origins:*}") List<String> allowedOrigins
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API stateless con JWT: no hay cookies de sesión que proteger
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Health check para Docker/CI: público y sin datos sensibles.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Handshake WebSocket: público a nivel HTTP; el frame STOMP
                        // CONNECT se autentica con JWT en WebSocketAuthChannelInterceptor.
                        .requestMatchers("/ws", "/ws/**", "/ws-sockjs", "/ws-sockjs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, cause) ->
                                writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                        "No autenticado: falta el token o no es válido", req.getRequestURI()))
                        .accessDeniedHandler((req, res, cause) ->
                                writeJson(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                        "No tienes permiso para acceder a este recurso", req.getRequestURI())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS: el SPA (React + Vite) corre en otro origen/puerto.
     * Los orígenes vienen de la propiedad app.cors.allowed-origins.
     *
     * allowCredentials(false) es intencional: la autenticación viaja en el
     * header Authorization (Bearer), no en cookies, así que no hacen falta
     * credenciales. Combinar "*" con credenciales(true) sí sería peligroso:
     * permitiría a cualquier sitio hacer peticiones con credenciales.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private void writeJson(HttpServletResponse response, int status, String error, String message, String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiError.of(status, error, message, path));
    }
}
