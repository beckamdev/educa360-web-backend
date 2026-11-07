package com.educa360.backend.service;

import com.educa360.backend.exception.BadRequestException;
import com.educa360.backend.exception.PayloadTooLargeException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Validación única de archivos para materiales y sustentos.
 *
 * Regla simple y explícita: la extensión decide el MIME esperado y ambos
 * deben coincidir. Se valida antes de tocar Storage para no subir basura.
 */
@Component
public class ArchivoValidator {

    private static final long MB = 1024L * 1024L;

    private static final Map<String, String> MATERIALES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("mp4", "video/mp4")
    );

    private static final Map<String, String> SUSTENTOS = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png"
    );

    public ArchivoValidado validarMaterial(MultipartFile archivo) {
        return validar(archivo, MATERIALES, 25 * MB,
                "El material no puede superar los 25 MB");
    }

    public ArchivoValidado validarSustento(MultipartFile archivo) {
        return validar(archivo, SUSTENTOS, 5 * MB,
                "El sustento no puede superar los 5 MB");
    }

    private ArchivoValidado validar(MultipartFile archivo, Map<String, String> permitidos,
                                    long maximo, String mensajeTamano) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("El archivo es obligatorio y no puede estar vacío");
        }
        if (archivo.getSize() > maximo) {
            throw new PayloadTooLargeException(mensajeTamano);
        }

        String original = archivo.getOriginalFilename() == null
                ? "archivo"
                : archivo.getOriginalFilename().trim();
        String extension = extensionDe(original);
        String mimeEsperado = permitidos.get(extension);
        if (mimeEsperado == null) {
            throw new BadRequestException("Extensión no permitida: ." + extension
                    + ". Permitidas: " + String.join(", ", permitidos.keySet()));
        }

        String mimeRecibido = normalizarMime(archivo.getContentType());
        if (mimeRecibido != null && !mimeRecibido.equals(mimeEsperado)) {
            throw new BadRequestException("El tipo MIME (" + mimeRecibido
                    + ") no corresponde a la extensión ." + extension);
        }

        try {
            return new ArchivoValidado(
                    nombreSeguro(original, extension),
                    recortar(original, 255),
                    mimeEsperado,
                    archivo.getSize(),
                    archivo.getBytes());
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo leer el archivo");
        }
    }

    private String extensionDe(String nombre) {
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            throw new BadRequestException("El archivo debe tener una extensión");
        }
        return nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizarMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return null;
        }
        String limpio = mime.toLowerCase(Locale.ROOT).split(";")[0].trim();
        return "image/jpg".equals(limpio) ? "image/jpeg" : limpio;
    }

    /**
     * Nombre seguro para la ruta de Storage: sin separadores ni caracteres
     * especiales. Se conserva la extensión y se recorta a 120 caracteres.
     */
    private String nombreSeguro(String original, String extension) {
        String base = original.substring(0, original.length() - extension.length() - 1)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank()) {
            base = "archivo";
        }
        int maxBase = 120 - extension.length() - 1;
        if (base.length() > maxBase) {
            base = base.substring(0, maxBase);
        }
        return base + "." + extension;
    }

    private String recortar(String valor, int maximo) {
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    public record ArchivoValidado(
            String nombreSeguro,
            String nombreOriginal,
            String mimeType,
            long tamanoBytes,
            byte[] contenido
    ) {
    }
}
