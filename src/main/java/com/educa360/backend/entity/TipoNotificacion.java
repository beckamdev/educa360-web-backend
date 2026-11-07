package com.educa360.backend.entity;

/**
 * Origen del aviso. No describe el contenido: eso vive en el evento
 * referenciado ({@link Notificacion#getReferenciaId()}).
 */
public enum TipoNotificacion {
    COMUNICADO,
    TICKET_RESUELTO,
    CALIFICACION,
    ASISTENCIA,
    MATERIAL,
    CIERRE
}
