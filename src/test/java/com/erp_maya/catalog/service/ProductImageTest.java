package com.erp_maya.catalog.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validación de la foto del producto antes de guardarla. */
class ProductImageTest {

    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    @Test
    void aceptaWebpJpgYPng() {
        assertArrayEquals(WEBP, ProductImageService.decodificar(b64(WEBP), "image/webp", 1024, "La imagen"));
        assertArrayEquals(JPG, ProductImageService.decodificar(b64(JPG), " IMAGE/JPEG ", 1024, "La imagen"));
        assertArrayEquals(PNG, ProductImageService.decodificar(b64(PNG), "image/png", 1024, "La imagen"));
    }

    @Test
    void rechazaTipoNoPermitido() {
        assertThrows(IllegalStateException.class,
                () -> ProductImageService.decodificar(b64(PNG), "image/gif", 1024, "La imagen"));
    }

    @Test
    void rechazaContenidoQueNoCoincideConElTipo() {
        assertFalse(ProductImageService.coincide(PNG, "image/webp"));
        assertTrue(ProductImageService.coincide(WEBP, "image/webp"));
        assertThrows(IllegalStateException.class,
                () -> ProductImageService.decodificar(b64(PNG), "image/jpeg", 1024, "La imagen"));
    }

    @Test
    void rechazaVaciaGrandeOMalCodificada() {
        assertThrows(IllegalStateException.class, () -> ProductImageService.decodificar("  ", "image/png", 1024, "La imagen"));
        assertThrows(IllegalStateException.class,
                () -> ProductImageService.decodificar(b64(new byte[2048]), "image/png", 1024, "La imagen"));
        assertThrows(IllegalStateException.class, () -> ProductImageService.decodificar("%%%%", "image/png", 1024, "La imagen"));
    }
}
