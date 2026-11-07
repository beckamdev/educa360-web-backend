package com.educa360.backend;

import com.educa360.backend.entity.User;
import com.educa360.backend.repository.NotificacionRepository;
import com.educa360.backend.repository.UserRepository;
import com.educa360.backend.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integración real del WebSocket STOMP:
 * - un CONNECT con JWT válido se acepta;
 * - un CONNECT sin token se rechaza;
 * - el destino /user/queue/notificaciones aísla a cada usuario.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler scheduler;
    private StompSession sesionAlumno;
    private StompSession sesionMama;

    @BeforeEach
    void preparar() {
        notificacionRepository.deleteAll();
        userRepository.deleteAll();

        User alumno = user("alma@educa360.pe", "30000001", User.Role.ESTUDIANTE);
        User mama = user("lucia@educa360.pe", "40000001", User.Role.APODERADO);
        userRepository.saveAll(java.util.List.of(alumno, mama));

        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
        stompClient.setTaskScheduler(scheduler);

        sesionAlumno = conectar(jwtService.generateToken(alumno));
        sesionMama = conectar(jwtService.generateToken(mama));
    }

    @AfterEach
    void limpiar() {
        if (sesionAlumno != null && sesionAlumno.isConnected()) {
            sesionAlumno.disconnect();
        }
        if (sesionMama != null && sesionMama.isConnected()) {
            sesionMama.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    @DisplayName("Cada usuario recibe solo sus notificaciones")
    void aislaDestinatarios() throws Exception {
        BlockingQueue<String> recibidasAlumno = new LinkedBlockingQueue<>();
        BlockingQueue<String> recibidasMama = new LinkedBlockingQueue<>();
        suscribir(sesionAlumno, recibidasAlumno);
        suscribir(sesionMama, recibidasMama);

        // El SUBSCRIBE viaja por la misma sesión y es asíncrono: se reintenta el
        // envío hasta que el broker tenga registrada la suscripción. Los envíos
        // previos se descartan, así que no se acumulan en la cola.
        enviarHastaRecibir("alma@educa360.pe", "para-alma", recibidasAlumno);

        assertNull(recibidasMama.poll(500, TimeUnit.MILLISECONDS),
                "la apoderada no debe recibir el mensaje del alumno");
    }

    @Test
    @DisplayName("Un CONNECT sin token se rechaza")
    void rechazaSinToken() {
        StompHeaders headers = new StompHeaders();

        assertThrows(ExecutionException.class, () -> stompClient.connectAsync(
                        url(), new WebSocketHttpHeaders(), headers,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS));
    }

    // ==================== Helpers ====================

    private StompSession conectar(String token) {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        try {
            return stompClient.connectAsync(url(), new WebSocketHttpHeaders(),
                            headers, new StompSessionHandlerAdapter() {
                            })
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo conectar el WebSocket", ex);
        }
    }

    private void suscribir(StompSession sesion, BlockingQueue<String> recibidas) {
        sesion.subscribe("/user/queue/notificaciones", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                recibidas.add((String) payload);
            }
        });
    }

    /**
     * Reintenta el envío hasta que la suscripción STOMP esté registrada en el
     * broker. Evita la carrera entre el frame SUBSCRIBE y el primer mensaje.
     */
    private void enviarHastaRecibir(String email, String payload,
                                    BlockingQueue<String> recibidas) throws Exception {
        for (int intento = 0; intento < 50; intento++) {
            messagingTemplate.convertAndSendToUser(
                    email, "/queue/notificaciones", payload);
            String recibido = recibidas.poll(200, TimeUnit.MILLISECONDS);
            if (recibido != null) {
                assertEquals(payload, recibido);
                return;
            }
        }
        fail("La suscripción STOMP no quedó registrada en el broker");
    }

    private String url() {
        return "ws://localhost:" + port + "/ws";
    }

    private User user(String email, String dni, User.Role rol) {
        return new User(email, passwordEncoder.encode("Password1234"),
                "Nombre", "Apellido", dni, rol);
    }
}
