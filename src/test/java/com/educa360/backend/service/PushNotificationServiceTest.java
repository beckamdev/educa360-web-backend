package com.educa360.backend.service;

import com.educa360.backend.dto.NotificacionResponse;
import com.educa360.backend.entity.Dispositivo;
import com.educa360.backend.entity.PlataformaDispositivo;
import com.educa360.backend.entity.TipoNotificacion;
import com.educa360.backend.entity.User;
import com.educa360.backend.repository.DispositivoRepository;
import com.educa360.backend.service.push.PushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock private DispositivoRepository dispositivoRepository;
    @Mock private PushSender pushSender;

    private PushNotificationService servicio;

    @BeforeEach
    void preparar() {
        // Executor síncrono: el test ve el envío sin esperas ni hilos.
        servicio = new PushNotificationService(
                dispositivoRepository, pushSender, Runnable::run);
    }

    @Test
    @DisplayName("Con el proveedor apagado no consulta dispositivos")
    void proveedorApagado() {
        when(pushSender.activo()).thenReturn(false);

        servicio.enviar(List.of(notificacion()), List.of(4L));

        verifyNoInteractions(dispositivoRepository);
        verify(pushSender, never()).enviar(anyList());
    }

    @Test
    @DisplayName("Envía a todos los dispositivos del usuario y borra el token inválido")
    void enviaYBorraTokenInvalido() {
        when(pushSender.activo()).thenReturn(true);
        User usuario = usuario(4L);
        Dispositivo bueno = new Dispositivo(usuario, "token-bueno",
                PlataformaDispositivo.ANDROID);
        Dispositivo malo = new Dispositivo(usuario, "token-malo",
                PlataformaDispositivo.IOS);
        when(dispositivoRepository.findByUsuarioIdIn(Set.of(4L)))
                .thenReturn(List.of(bueno, malo));
        when(pushSender.enviar(anyList()))
                .thenReturn(List.of(PushSender.Resultado.ENVIADO,
                        PushSender.Resultado.TOKEN_INVALIDO));

        servicio.enviar(List.of(notificacion()), List.of(4L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushSender.MensajePush>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(pushSender).enviar(captor.capture());
        assertEquals(List.of("token-bueno", "token-malo"),
                captor.getValue().stream().map(PushSender.MensajePush::token).toList());
        verify(dispositivoRepository).eliminarPorTokens(List.of("token-malo"));
    }

    @Test
    @DisplayName("Un comunicado masivo se parte en lotes de 100")
    void parteEnLotesDeCien() {
        when(pushSender.activo()).thenReturn(true);
        User usuario = usuario(4L);
        List<Dispositivo> dispositivos = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            dispositivos.add(new Dispositivo(usuario, "token-" + i,
                    PlataformaDispositivo.ANDROID));
        }
        when(dispositivoRepository.findByUsuarioIdIn(Set.of(4L)))
                .thenReturn(dispositivos);
        when(pushSender.enviar(anyList())).thenAnswer(invocacion -> {
            List<PushSender.MensajePush> lote = invocacion.getArgument(0);
            return lote.stream().map(m -> PushSender.Resultado.ENVIADO).toList();
        });

        servicio.enviar(List.of(notificacion()), List.of(4L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushSender.MensajePush>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(pushSender, times(2)).enviar(captor.capture());
        assertEquals(List.of(100, 50),
                captor.getAllValues().stream().map(List::size).toList());
        verify(dispositivoRepository, never()).eliminarPorTokens(anyList());
    }

    @Test
    @DisplayName("Si las listas no se alinean, no envía nada")
    void listasDesalineadas() {
        when(pushSender.activo()).thenReturn(true);

        servicio.enviar(List.of(notificacion(), notificacion()), List.of(4L));

        verifyNoInteractions(dispositivoRepository);
        verify(pushSender, never()).enviar(anyList());
    }

    private NotificacionResponse notificacion() {
        return new NotificacionResponse(
                99L,
                TipoNotificacion.COMUNICADO,
                "Aviso",
                "Mensaje",
                1L,
                false,
                null,
                LocalDateTime.of(2026, 3, 9, 8, 0));
    }

    private User usuario(Long id) {
        User user = new User("u" + id + "@educa360.pe", "x", "Nombre", "Apellido",
                "dni-" + id, User.Role.ESTUDIANTE);
        user.setId(id);
        return user;
    }
}
