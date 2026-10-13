package com.erp_maya.catalog.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Patrón de búsqueda de productos ({@link ProductService#patron}).
 *
 * <p>El repositorio compara contra {@code translate(lower(name), tildes, sin tildes)},
 * así que el texto que escribe el usuario tiene que llegar igual: en
 * minúsculas, sin tildes y sin comodines propios. Así «pérgola», «Pergola» y
 * «PÉRGOLA» encuentran el mismo producto.
 */
class ProductSearchPatternTest {

    @Test
    void quitaTildesYMayusculas() {
        assertEquals("%pergola%", ProductService.patron("Pérgola"));
        assertEquals("%pergola%", ProductService.patron("PÉRGOLA"));
        assertEquals("%nandu%", ProductService.patron("Ñandú"));
    }

    @Test
    void quitaEspaciosDeLosLadosYComodinesDelUsuario() {
        assertEquals("%cama 50%", ProductService.patron("  cama_ 50%  "));
        assertEquals("%%", ProductService.patron("%_"));
    }
}
