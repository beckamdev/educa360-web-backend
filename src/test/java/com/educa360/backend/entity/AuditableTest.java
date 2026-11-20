package com.educa360.backend.entity;

import com.educa360.backend.config.ZonaHoraria;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La auditoría debe escribirse en hora de Perú aunque el servidor arranque en
 * UTC (Railway/Docker). Si alguien vuelve a {@code LocalDateTime.now()} sin
 * zona, este test falla con una diferencia de ~5 horas.
 */
class AuditableTest {

    @Test
    void laAuditoriaUsaLaHoraDePeru() {
        Auditable entidad = new Auditable() {
        };

        entidad.onCreate();

        long segundos = Math.abs(Duration.between(
                entidad.getCreatedAt(),
                LocalDateTime.now(ZonaHoraria.APP)).getSeconds());

        assertTrue(segundos < 5,
                "createdAt debería estar en hora de Lima; diferencia de " + segundos + "s");
    }
}
