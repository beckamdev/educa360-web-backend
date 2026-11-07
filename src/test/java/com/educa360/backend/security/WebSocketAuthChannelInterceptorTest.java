package com.educa360.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;

    @InjectMocks private WebSocketAuthChannelInterceptor interceptor;

    private final MessageChannel canal = mock(MessageChannel.class);

    @Test
    @DisplayName("Acepta un CONNECT con JWT válido y asocia el usuario")
    void aceptaTokenValido() {
        UserDetails user = user();
        when(jwtService.extractEmail("token")).thenReturn("alma@educa360.pe");
        when(userDetailsService.loadUserByUsername("alma@educa360.pe")).thenReturn(user);
        when(jwtService.isTokenValid("token", user)).thenReturn(true);

        Message<?> resultado = interceptor.preSend(connect("Bearer token"), canal);

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(resultado, StompHeaderAccessor.class);
        assertNotNull(accessor);
        assertNotNull(accessor.getUser());
        assertEquals("alma@educa360.pe", accessor.getUser().getName());
    }

    @Test
    @DisplayName("Acepta el token sin el prefijo Bearer")
    void aceptaTokenSinPrefijo() {
        UserDetails user = user();
        when(jwtService.extractEmail("token")).thenReturn("alma@educa360.pe");
        when(userDetailsService.loadUserByUsername("alma@educa360.pe")).thenReturn(user);
        when(jwtService.isTokenValid("token", user)).thenReturn(true);

        Message<?> resultado = interceptor.preSend(connect("token"), canal);

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(resultado, StompHeaderAccessor.class);
        assertNotNull(accessor.getUser());
    }

    @Test
    @DisplayName("Rechaza un CONNECT sin token")
    void rechazaSinToken() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connect(null), canal));
    }

    @Test
    @DisplayName("Rechaza un token malformado")
    void rechazaTokenMalformado() {
        when(jwtService.extractEmail("roto"))
                .thenThrow(new IllegalArgumentException("token inválido"));

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connect("Bearer roto"), canal));
    }

    @Test
    @DisplayName("Rechaza un token expirado o inválido")
    void rechazaTokenInvalido() {
        UserDetails user = user();
        when(jwtService.extractEmail("token")).thenReturn("alma@educa360.pe");
        when(userDetailsService.loadUserByUsername("alma@educa360.pe")).thenReturn(user);
        when(jwtService.isTokenValid("token", user)).thenReturn(false);

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connect("Bearer token"), canal));
    }

    @Test
    @DisplayName("Ignora los comandos que no son CONNECT")
    void ignoraOtrosComandos() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> mensaje = MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());

        assertSame(mensaje, interceptor.preSend(mensaje, canal));
    }

    private Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UserDetails user() {
        return org.springframework.security.core.userdetails.User
                .withUsername("alma@educa360.pe")
                .password("password")
                .authorities("ROLE_ESTUDIANTE")
                .build();
    }
}
