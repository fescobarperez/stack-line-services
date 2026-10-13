package com.erp_maya.quote.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Redondeo automático del total de la cotización
 * ({@code quotes.rounding_step} y {@code quotes.rounding_mode}).
 *
 * <p>El paso es el múltiplo al que se lleva el total (1, 5, 10, 50, 100) y el
 * modo, hacia dónde: {@code down} (nunca cobra de más), {@code nearest} o
 * {@code up}.
 */
class QuoteRoundingTest {

    private static BigDecimal q(String v) { return new BigDecimal(v); }

    @Test
    void haciaAbajoAlMultiploDeCinco() {
        // El caso de Madera Viva: Q 8,995.50 → Q 8,995.
        assertEquals(q("8995.00"), QuoteChargeService.redondear(q("8995.50"), q("5"), "down"));
        assertEquals(q("8995.00"), QuoteChargeService.redondear(q("8999.99"), q("5"), "down"));
    }

    @Test
    void alMasCercanoYHaciaArriba() {
        assertEquals(q("9000.00"), QuoteChargeService.redondear(q("8997.50"), q("5"), "nearest"));
        assertEquals(q("8990.00"), QuoteChargeService.redondear(q("8991.00"), q("10"), "nearest"));
        assertEquals(q("9000.00"), QuoteChargeService.redondear(q("8991.00"), q("10"), "up"));
    }

    @Test
    void unMultiploExactoNoCambia() {
        assertEquals(q("9000.00"), QuoteChargeService.redondear(q("9000"), q("100"), "up"));
    }
}
