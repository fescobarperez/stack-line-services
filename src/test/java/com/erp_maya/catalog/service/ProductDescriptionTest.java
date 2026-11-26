package com.erp_maya.catalog.service;

import com.erp_maya.catalog.dto.ProductRequest;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Descripción del producto: quien edita sin conocer el campo (una pantalla
 * vieja, una integración) no la borra; vacía sí la quita.
 */
class ProductDescriptionTest {

    @Test
    void sin_el_campo_se_queda_la_que_habia() {
        assertEquals("Pérgola de pino tratado", ProductService.descripcion("Pérgola de pino tratado", null));
        assertNull(ProductService.descripcion(null, null));
    }

    @Test
    void la_nueva_reemplaza_sin_espacios_sobrantes() {
        assertEquals("Deck de teca", ProductService.descripcion("Vieja", "  Deck de teca \n"));
        assertEquals("Línea 1\n\nLínea 2", ProductService.descripcion(null, "Línea 1\n\nLínea 2"));
    }

    @Test
    void vacia_la_quita() {
        assertNull(ProductService.descripcion("Vieja", ""));
        assertNull(ProductService.descripcion("Vieja", "   \n "));
    }

    @Test
    void el_tope_de_largo_esta_en_la_validacion() throws Exception {
        Size tope = ProductRequest.class.getDeclaredField("description").getAnnotation(Size.class);
        assertEquals(ProductRequest.MAX_DESCRIPTION, tope.max());
    }
}
