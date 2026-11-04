package com.educa360.backend.service;

import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.PayloadTooLargeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchivoValidatorTest {

    private final ArchivoValidator validador = new ArchivoValidator();

    @Test
    @DisplayName("Un PDF válido conserva extensión y MIME")
    void aceptaPdf() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "Guía de clase.pdf", "application/pdf", "%PDF-1.4".getBytes());

        ArchivoValidator.ArchivoValidado validado = validador.validarMaterial(archivo);

        assertEquals("application/pdf", validado.mimeType());
        assertEquals("Gu_a_de_clase.pdf", validado.nombreSeguro());
        assertTrue(validado.contenido().length > 0);
    }

    @Test
    @DisplayName("Una extensión no permitida se rechaza")
    void rechazaExtension() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "script.exe", "application/octet-stream", "MZ".getBytes());

        assertThrows(BadRequestException.class, () -> validador.validarMaterial(archivo));
    }

    @Test
    @DisplayName("El MIME debe coincidir con la extensión")
    void rechazaMimeQueNoCoincide() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "foto.pdf", "image/png", "PNG".getBytes());

        assertThrows(BadRequestException.class, () -> validador.validarMaterial(archivo));
    }

    @Test
    @DisplayName("Un archivo vacío se rechaza")
    void rechazaArchivoVacio() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "vacio.pdf", "application/pdf", new byte[0]);

        assertThrows(BadRequestException.class, () -> validador.validarMaterial(archivo));
    }

    @Test
    @DisplayName("El sustento supera 5 MB y responde 413")
    void rechazaSustentoGrande() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "grande.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]);

        assertThrows(PayloadTooLargeException.class,
                () -> validador.validarSustento(archivo));
    }
}
