package com.erp_maya.quote.service;

import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Precios al cliente del carrito: la ganancia y los cargos van dentro del
 * precio de cada producto, así las líneas suman el subtotal.
 */
class QuoteClientPricesTest {

    private static BigDecimal q(String v) { return new BigDecimal(v); }

    private static QuoteItem item(long id, String cantidad, String total, Boolean visible) {
        QuoteItem i = new QuoteItem();
        i.setId(id);
        i.setQuantity(q(cantidad));
        i.setLineTotal(q(total));
        i.setShowToClient(visible);
        return i;
    }

    @Test
    void unProductoLlevaTodoElSubtotal() {
        // El caso de la pérgola: línea Q 20,000 y subtotal Q 26,000 (30% entre ganancia y cargos).
        var r = QuoteChargeService.preciosAlCliente(List.of(item(1, "1", "20000.00", true)), q("26000.00"));
        assertEquals(q("26000.00"), r.get(0).lineTotal());
        assertEquals(q("26000.00"), r.get(0).unitPrice());
    }

    @Test
    void seRepartePorProporcionYSumaElSubtotal() {
        var r = QuoteChargeService.preciosAlCliente(List.of(
                item(1, "1", "1000.00", true),
                item(2, "3", "2000.00", true)), q("3900.00"));
        assertEquals(q("1300.00"), r.get(0).lineTotal());
        assertEquals(q("2600.00"), r.get(1).lineTotal());
        assertEquals(q("866.67"), r.get(1).unitPrice());
        assertEquals(q("3900.00"), r.get(0).lineTotal().add(r.get(1).lineTotal()));
    }

    @Test
    void losCentavosLosAbsorbeLaUltimaLinea() {
        var r = QuoteChargeService.preciosAlCliente(List.of(
                item(1, "1", "100.00", true), item(2, "1", "100.00", true), item(3, "1", "100.00", true)), q("1000.00"));
        BigDecimal suma = r.stream().map(QuoteChargeDtos.ClientLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(q("1000.00"), suma);
        assertEquals(q("333.34"), r.get(2).lineTotal());
    }

    @Test
    void ocultasYSinPrecioNoRecibenCargos() {
        var r = QuoteChargeService.preciosAlCliente(List.of(
                item(1, "1", "1000.00", true),
                item(2, "1", "50.00", false),
                item(3, "2", "0", true)), q("1365.00"));
        // Lo oculto se reparte en lo visible: el cliente ve un total que cuadra.
        assertEquals(q("1365.00"), r.get(0).lineTotal());
        assertNull(r.get(1).lineTotal());
        assertEquals(q("0.00"), r.get(2).lineTotal());
    }

    @Test
    void laFormulaDeTotalesEsLaDeLaCotizacion() {
        // Pérgola Q 20,000 con 30% de ganancia sobre el costo total y 12% de IVA.
        var t = QuoteChargeService.totales(q("20000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                BigDecimal.ZERO, "percent", q("30.00"), QuoteChargeService.BASE_COSTO_TOTAL, BigDecimal.ZERO,
                q("12"), BigDecimal.ZERO, "down");
        assertEquals(q("26000.00"), t.adjustedBase());
        assertEquals(q("3120.00"), t.tax());
        assertEquals(q("29120.00"), t.total());
    }

    @Test
    void losCargosEntranAntesDeLaGanancia() {
        // Q 1,000 de líneas + Q 100 de cargo operativo; 10% de ganancia sobre el costo total.
        var t = QuoteChargeService.totales(q("1000.00"), q("100.00"), BigDecimal.ZERO, q("100.00"), false,
                BigDecimal.ZERO, "percent", q("10.00"), QuoteChargeService.BASE_COSTO_TOTAL, BigDecimal.ZERO,
                q("12"), BigDecimal.ZERO, "down");
        assertEquals(q("1210.00"), t.adjustedBase());
    }

    @Test
    void repartirRespetaElOrdenYLosCeros() {
        var r = QuoteChargeService.repartir(java.util.Arrays.asList(q("100"), null, q("300"), BigDecimal.ZERO), q("520.00"));
        assertEquals(q("130.00"), r.get(0));
        assertEquals(q("0.00"), r.get(1));
        assertEquals(q("390.00"), r.get(2));
        assertEquals(q("0.00"), r.get(3));
    }
}
