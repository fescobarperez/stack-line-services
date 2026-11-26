package com.erp_maya.catalog.service;

import com.erp_maya.catalog.domain.Category;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Filtro por categoría: una categoría trae también lo de sus subcategorías. */
class ProductCategoryFilterTest {

    private static Category cat(long id, Long padre) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(padre);
        return c;
    }

    // Hogar(1) → Cocinas(2), Closets(3) → Puertas(4); Muros(5) aparte.
    private final List<Category> arbol = List.of(cat(1, null), cat(2, 1L), cat(3, 1L), cat(4, 3L), cat(5, null));

    @Test
    void la_raiz_incluye_todas_sus_subcategorias() {
        assertEquals(List.of(1L, 2L, 3L, 4L), ProductService.conSubcategorias(arbol, 1L));
    }

    @Test
    void una_hoja_es_solo_ella() {
        assertEquals(List.of(4L), ProductService.conSubcategorias(arbol, 4L));
        assertEquals(List.of(5L), ProductService.conSubcategorias(arbol, 5L));
    }

    @Test
    void una_categoria_de_otra_empresa_no_trae_nada() {
        assertTrue(ProductService.conSubcategorias(arbol, 99L).isEmpty());
    }

    @Test
    void un_ciclo_no_la_cuelga() {
        List<Category> ciclo = List.of(cat(1, 2L), cat(2, 1L));
        assertEquals(List.of(1L, 2L), ProductService.conSubcategorias(ciclo, 1L));
    }
}
