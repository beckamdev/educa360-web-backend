package com.educa360.backend.entity;

/**
 * Sistema operativo del dispositivo que registra su token de push.
 *
 * La plataforma es informativa: Expo Push enruta el token a FCM o APNs
 * según corresponda. Se guarda para diagnóstico y para futuros filtros
 * (por ejemplo, no mandar un aviso sólo-web a un celular).
 */
public enum PlataformaDispositivo {
    ANDROID,
    IOS,
    WEB
}
