package com.erp_maya.quote.service;

import com.erp_maya.quote.service.QuoteChargeService.CargoDefecto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Qué cargos por defecto se copian a una cotización nueva ({@link QuoteChargeService#aplicables}). */
class DefaultChargesTest {

    private static CargoDefecto cargo(Long cat, String tipo, String valor) {
        return new CargoDefecto(null, cat, null, tipo, valor == null ? null : new BigDecimal(valor));
    }

    @Test
    void copiaLosValidosYSaltaLosDemas() {
        List<CargoDefecto> lista = List.of(
                cargo(1L, "percent", "10"),     // sí
                cargo(2L, "fixed", "50"),       // sí
                cargo(3L, "percent", "10"),     // concepto inactivo
                cargo(1L, "percent", "120"),    // porcentaje fuera de rango
                cargo(1L, "fixed", "0"),        // sin valor
                cargo(1L, "otro", "5"),         // tipo inválido
                cargo(null, "fixed", "5"),      // sin concepto
                new CargoDefecto("profit", null, null, "percent", new BigDecimal("30"))); // la ganancia no es cargo
        List<CargoDefecto> out = QuoteChargeService.aplicables(lista, Set.of(1L, 2L));
        assertEquals(2, out.size());
        assertEquals(1L, out.get(0).categoryId());
        assertEquals(2L, out.get(1).categoryId());
    }

    @Test
    void laGananciaSaleDeSuFila() {
        var lista = List.of(cargo(1L, "percent", "10"),
                new CargoDefecto("profit", null, null, "fixed", new BigDecimal("500")));
        var g = QuoteChargeService.ganancia(lista).orElseThrow();
        assertEquals("fixed", g.calcType());
        assertEquals(new BigDecimal("500"), g.value());
        assertEquals(java.util.Optional.empty(), QuoteChargeService.ganancia(List.of(cargo(1L, "percent", "10"))));
    }
}
