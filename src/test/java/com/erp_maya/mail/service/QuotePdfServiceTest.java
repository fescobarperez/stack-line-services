package com.erp_maya.mail.service;

import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de presentación del PDF que ve el cliente.
 *
 * <p>La más importante es {@link QuotePdfService#totalesAlCliente}: la ganancia,
 * el ajuste de cierre y el redondeo no salen como renglón propio; se reparten
 * entre las líneas en proporción a su precio, para que cada línea muestre lo
 * que de verdad paga el cliente y la suma cuadre con el subtotal. Los cargos
 * adicionales y gastos operativos sí van en su renglón, así que se restan.
 */
class QuotePdfServiceTest {

    private static BigDecimal q(String v) { return new BigDecimal(v); }

    private static QuoteItem linea(long id, String total) {
        QuoteItem i = new QuoteItem();
        i.setId(id);
        i.setQuantity(BigDecimal.ONE);
        i.setLineTotal(q(total));
        return i;
    }

    private static Quote cotizacion(QuoteItem... items) {
        Quote c = new Quote();
        c.setItems(List.of(items));
        return c;
    }

    /** Resumen de cargos con solo lo que usa el PDF: base ajustada, cargos y gastos. */
    private static QuoteChargeDtos.Summary resumen(String baseAjustada, String cargos, String operativos) {
        return new QuoteChargeDtos.Summary(null, null, operativos == null ? null : q(operativos), null, null, null,
                cargos == null ? null : q(cargos), null, null, null, null, null, null, null, null,
                q(baseAjustada), null, null, null, List.of(), null, null, null);
    }

    // ── totalesAlCliente ────────────────────────────────────────────────────

    @Test
    void laGananciaSeRepartePorPesoDeCadaLinea() {
        // 10,000 + 5,000 con 30 % de ganancia → base 19,500.
        Map<Long, BigDecimal> t = QuotePdfService.totalesAlCliente(
                cotizacion(linea(1, "10000"), linea(2, "5000")), resumen("19500", null, null));
        assertEquals(q("13000.00"), t.get(1L));
        assertEquals(q("6500.00"), t.get(2L));
    }

    @Test
    void losCargosVanEnSuRenglonYNoSeRepartenEnLasLineas() {
        // Base 20,000 de la que 300 son cargos y 200 gastos: a las líneas les toca 19,500.
        Map<Long, BigDecimal> t = QuotePdfService.totalesAlCliente(
                cotizacion(linea(1, "10000"), linea(2, "5000")), resumen("20000", "300", "200"));
        assertEquals(q("13000.00"), t.get(1L));
        assertEquals(q("6500.00"), t.get(2L));
    }

    @Test
    void laUltimaLineaAbsorbeLosCentavos() {
        Map<Long, BigDecimal> t = QuotePdfService.totalesAlCliente(
                cotizacion(linea(1, "100"), linea(2, "100"), linea(3, "100")), resumen("100", null, null));
        assertEquals(q("33.33"), t.get(1L));
        assertEquals(q("33.33"), t.get(2L));
        assertEquals(q("33.34"), t.get(3L));
        assertEquals(q("100.00"), t.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void sinResumenOSinLineasSeUsaElTotalDeCadaLinea() {
        assertTrue(QuotePdfService.totalesAlCliente(cotizacion(linea(1, "100")), null).isEmpty());
        assertTrue(QuotePdfService.totalesAlCliente(cotizacion(linea(1, "0")), resumen("100", null, null)).isEmpty());
    }

    // ── medidas ─────────────────────────────────────────────────────────────

    private static QuoteItem conMedidas(String alto, String ancho, String prof, String sizePricing) {
        QuoteItem i = linea(1, "1");
        i.setHeight(q(alto));
        i.setWidth(q(ancho));
        i.setDepth(q(prof));
        i.setBaseHeight(q("2.40"));
        i.setBaseWidth(q("3.00"));
        i.setBaseDepth(q("4.00"));
        i.setSizePricing(sizePricing);
        return i;
    }

    @Test
    void laMedidaDelCatalogoNoSeMarcaComoEspecial() {
        String texto = QuotePdfService.medidas(conMedidas("2.40", "3.00", "4.00", null));
        assertEquals("2.40 × 3.00 × 4.00 m", texto);
    }

    @Test
    void laMedidaEspecialSeMarcaYSiNoTienePrecioSeAvisa() {
        assertEquals("2.50 × 3.00 × 4.50 m · medida especial",
                QuotePdfService.medidas(conMedidas("2.50", "3.00", "4.50", "calculated")));
        String revisar = QuotePdfService.medidas(conMedidas("2.40", "4.00", "4.00", "review"));
        assertTrue(revisar.endsWith("· medida especial · precio por confirmar"));
    }

    @Test
    void sinMedidasNoHayTexto() {
        assertEquals("", QuotePdfService.medidas(linea(1, "1")));
    }

    // ── vigencia ────────────────────────────────────────────────────────────

    @Test
    void laVigenciaSeExpresaEnDiasComoEnLaCotizacionEnPapel() {
        Quote c = cotizacion();
        c.setQuoteDate(LocalDate.of(2026, 10, 13));
        c.setValidUntil(LocalDate.of(2026, 10, 16));
        assertEquals("3 días", QuotePdfService.vigenciaPrincipal(c));
        assertTrue(QuotePdfService.vigenciaDetalle(c).startsWith("Hasta el "));
        c.setValidUntil(LocalDate.of(2026, 10, 14));
        assertEquals("1 día", QuotePdfService.vigenciaPrincipal(c));
    }

    @Test
    void sinFechaDeVencimientoNoHayVigencia() {
        Quote c = cotizacion();
        assertEquals("—", QuotePdfService.vigenciaPrincipal(c));
        assertEquals("Propuesta comercial", QuotePdfService.vigenciaDetalle(c));
        assertFalse(QuotePdfService.vigenciaDetalle(c).isBlank());
    }
}
