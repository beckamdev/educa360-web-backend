package com.educa360.backend.config;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Zona horaria de la aplicación.
 *
 * El colegio opera en hora de Perú y el esquema guarda los timestamps como
 * {@code timestamp without time zone}. Por eso la auditoría, las fechas de
 * negocio y la tardanza se calculan siempre con esta zona explícita, sin
 * depender de la zona del servidor: Railway y Docker arrancan en UTC.
 */
public final class ZonaHoraria {

    public static final ZoneId APP = ZoneId.of("America/Lima");

    private ZonaHoraria() {
    }

    /** Reloj de la aplicación; los tests pueden inyectar uno fijo. */
    public static Clock reloj() {
        return Clock.system(APP);
    }
}
