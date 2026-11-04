package com.educa360.backend.exception;

/** 413: el archivo supera el tamaño permitido para su tipo. */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String mensaje) {
        super(mensaje);
    }
}
