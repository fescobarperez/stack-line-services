package com.erp_maya.quote.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** La imagen del diseño: base64, solo PNG/JPEG/WebP y con tope de tamaño ({@link QuoteDesignService#decodificar}). */
class QuoteDesignImageTest {

    @Test
    void decodificaUnaImagenValida() {
        byte[] bytes = {1, 2, 3, 4};
        assertArrayEquals(bytes, QuoteDesignService.decodificar(Base64.getEncoder().encodeToString(bytes), "image/png"));
    }

    @Test
    void sinImagenDevuelveNulo() {
        assertNull(QuoteDesignService.decodificar(null, "image/png"));
        assertNull(QuoteDesignService.decodificar("  ", null));
    }

    @Test
    void rechazaOtrosTiposBase64InvalidoYArchivosGrandes() {
        assertThrows(IllegalStateException.class, () -> QuoteDesignService.decodificar("AAAA", "application/pdf"));
        assertThrows(IllegalStateException.class, () -> QuoteDesignService.decodificar("no es base64 !!", "image/png"));
        String enorme = "A".repeat(QuoteDesignService.MAX_BYTES / 3 * 4 + 400);
        assertThrows(IllegalStateException.class, () -> QuoteDesignService.decodificar(enorme, "image/jpeg"));
    }
}
