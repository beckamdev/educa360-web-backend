package com.educa360.backend.config;

import com.educa360.backend.service.push.ExpoPushSender;
import com.educa360.backend.service.push.NoopPushSender;
import com.educa360.backend.service.push.PushSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Selección del proveedor de push y del hilo que lo ejecuta.
 *
 * {@code app.push.enabled=false} (por defecto) usa {@link NoopPushSender}:
 * la API funciona igual y no se consultan dispositivos. En producción se
 * activa con {@code APP_PUSH_ENABLED=true}; las credenciales de FCM/APNs
 * viven en EAS (proyecto móvil), no en este backend.
 */
@Configuration
public class PushConfig {

    @Bean
    @ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
    public PushSender expoPushSender(
            @Value("${app.push.expo.url:https://exp.host/--/api/v2/push/send}") String url,
            @Value("${app.push.expo.access-token:}") String accessToken) {
        return new ExpoPushSender(url, accessToken);
    }

    @Bean
    @ConditionalOnProperty(name = "app.push.enabled", havingValue = "false",
            matchIfMissing = true)
    public PushSender noopPushSender() {
        return new NoopPushSender();
    }

    /**
     * Un hilo basta para el volumen de un colegio y evita ráfagas de hilos.
     * La cola es acotada: si se llena (un evento masivo con Expo caído) se
     * descartan pushes, nunca se bloquea la petición ni la transacción.
     */
    @Bean(name = "pushExecutor")
    public Executor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("push-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
