package com.educa360.backend.security;

import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Autentica el frame STOMP CONNECT con el mismo JWT de la API REST.
 *
 * El navegador no puede enviar headers en un WebSocket nativo, pero STOMP sí
 * permite headers en el frame CONNECT. El cliente envía:
 *
 *   Authorization: Bearer &lt;token&gt;
 *
 * Sin token válido el CONNECT se rechaza y no se crea la sesión STOMP.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService,
                                           UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = tokenDe(accessor);
        if (token == null) {
            throw new MessagingException(
                    "No autenticado: falta el token en el frame CONNECT");
        }

        try {
            String email = jwtService.extractEmail(token);
            UserDetails user = userDetailsService.loadUserByUsername(email);
            if (!jwtService.isTokenValid(token, user)) {
                throw new MessagingException("Token inválido o expirado");
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities());
            accessor.setUser(authentication);
            return message;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            throw new MessagingException("Token inválido o expirado", ex);
        }
    }

    private String tokenDe(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (header == null || header.isBlank()) {
            return null;
        }
        String token = header.startsWith(BEARER) ? header.substring(BEARER.length()) : header;
        return token.isBlank() ? null : token;
    }
}
