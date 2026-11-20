package com.educa360.backend.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZonaHorariaTest {

    @Test
    void elRelojDeLaAplicacionUsaLaHoraDePeru() {
        assertEquals(ZoneId.of("America/Lima"), ZonaHoraria.reloj().getZone());
    }
}
