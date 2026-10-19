package com.erp_maya.catalog.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La categoría que propone el asistente se busca por nombre sin distinguir
 * mayúsculas ni tildes ({@link ProductService#normalizar}).
 */
class ProductReviewNamesTest {

    @Test
    void ignoraMayusculasTildesYEspacios() {
        assertEquals("pergolas", ProductService.normalizar("  Pérgolas "));
        assertEquals(ProductService.normalizar("Muebles de exterior"), ProductService.normalizar("MUEBLES DE EXTERIOR"));
        assertEquals("diseno", ProductService.normalizar("Diseño"));
    }
}
