package com.erp_maya.quote.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Precio automático de una medida especial ({@link QuoteService#calcularPorMedida}).
 *
 * <p>Fórmula: {@code precio = precio del catálogo × (fijo + (1 − fijo) × volumen pedido ÷ volumen del catálogo)}.
 * La parte fija ({@code quotes.size_fixed_pct}, 30 % por defecto) es lo que no
 * cambia con el tamaño: mano de obra, herrajes, instalación. El volumen se
 * calcula solo con las medidas que existen en el catálogo y en el pedido.
 *
 * <p>Si alguna medida se aleja de la del catálogo más que la tolerancia
 * ({@code quotes.size_tolerance_pct}, 25 % por defecto), o el producto no tiene
 * precio, la línea queda en 0 con estado {@code review}: la cotiza el asesor.
 */
class QuoteSizePricingTest {

    private static final BigDecimal FIJO = q("30");
    private static final BigDecimal TOLERANCIA = q("25");
    /** Pérgola del catálogo: alto 2.40 × ancho 3.00 × profundidad 4.00 a Q 20,000. */
    private static final BigDecimal[] PERGOLA = {q("2.40"), q("3.00"), q("4.00")};

    private static BigDecimal q(String v) { return new BigDecimal(v); }

    private static BigDecimal[] medida(String alto, String ancho, String profundidad) {
        return new BigDecimal[]{
                alto == null ? null : q(alto),
                ancho == null ? null : q(ancho),
                profundidad == null ? null : q(profundidad)};
    }

    @Test
    void masGrandeDentroDeLaTolerancia() {
        // 2.50 × 3.00 × 4.50: volumen ×1.171875 → 20,000 × (0.30 + 0.70 × 1.171875).
        var r = QuoteService.calcularPorMedida(q("20000"), PERGOLA, medida("2.50", "3.00", "4.50"), FIJO, TOLERANCIA);
        assertEquals("calculated", r.estado());
        assertEquals(q("22406.25"), r.precio());
    }

    @Test
    void masPequenaTambienBajaElPrecio() {
        // 2.00 × 3.00 × 4.00: volumen ×0.833333 → 20,000 × 0.8833331.
        var r = QuoteService.calcularPorMedida(q("20000"), PERGOLA, medida("2.00", "3.00", "4.00"), FIJO, TOLERANCIA);
        assertEquals("calculated", r.estado());
        assertEquals(q("17666.66"), r.precio());
    }

    @Test
    void justoEnElLimiteDeLaToleranciaSeCalcula() {
        // Ancho 3.00 sobre 2.40 es exactamente +25 %: todavía entra.
        var r = QuoteService.calcularPorMedida(q("20000"), PERGOLA, medida("3.00", "3.00", "4.00"), FIJO, TOLERANCIA);
        assertEquals("calculated", r.estado());
        assertEquals(q("23500.00"), r.precio());
    }

    @Test
    void fueraDeToleranciaLoCotizaElAsesor() {
        // Ancho 4.00 sobre 3.00 es +33 %: se sale del 25 %.
        var r = QuoteService.calcularPorMedida(q("20000"), PERGOLA, medida("2.40", "4.00", "4.00"), FIJO, TOLERANCIA);
        assertEquals("review", r.estado());
        assertEquals(BigDecimal.ZERO, r.precio());
    }

    @Test
    void soloSeComparanLasMedidasQueExistenEnAmbosLados() {
        // Mueble con solo alto en el catálogo (1.80): el ancho pedido no cuenta.
        var r = QuoteService.calcularPorMedida(q("10000"), medida("1.80", null, null),
                medida("2.00", "1.50", null), FIJO, TOLERANCIA);
        assertEquals("calculated", r.estado());
        assertEquals(q("10777.78"), r.precio());
    }

    @Test
    void sinMedidasEnComunNoHayCalculo() {
        var r = QuoteService.calcularPorMedida(q("10000"), medida("1.80", null, null),
                medida(null, "1.50", null), FIJO, TOLERANCIA);
        assertEquals("review", r.estado());
    }

    @Test
    void sinPrecioDeCatalogoLoCotizaElAsesor() {
        var r = QuoteService.calcularPorMedida(BigDecimal.ZERO, PERGOLA, medida("2.50", "3.00", "4.50"), FIJO, TOLERANCIA);
        assertEquals("review", r.estado());
        r = QuoteService.calcularPorMedida(null, PERGOLA, medida("2.50", "3.00", "4.50"), FIJO, TOLERANCIA);
        assertEquals("review", r.estado());
    }

    @Test
    void conTodoFijoElTamanoNoMueveElPrecio() {
        var r = QuoteService.calcularPorMedida(q("20000"), PERGOLA, medida("2.50", "3.00", "4.50"), q("100"), TOLERANCIA);
        assertEquals(q("20000.00"), r.precio());
    }
}
