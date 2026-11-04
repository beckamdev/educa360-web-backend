package com.educa360.backend.exception;

/** 503: el almacenamiento de archivos no está configurado o no responde. */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String mensaje) {
        super(mensaje);
    }

    public StorageUnavailableException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
