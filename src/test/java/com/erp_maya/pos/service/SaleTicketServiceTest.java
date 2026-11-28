package com.erp_maya.pos.service;

import com.erp_maya.pos.dto.SaleDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El ticket sale con los datos de la venta, nunca con valores de ejemplo. */
class SaleTicketServiceTest {

    private static SaleDtos.Ticket ticket(SaleDtos.TicketFel fel, String docNumber) {
        return new SaleDtos.Ticket(7L, "FACT", docNumber, Instant.parse("2026-11-28T05:22:00Z"),
                "Madera Viva", "Madera Viva, S.A.", "1234567-8", "Km 15 <Carretera>", "+502 3050 8989",
                "ventas@maderaviva.gt", "Central", "Ana López", "Juan Pérez", "CF", null,
                List.of(new SaleDtos.TicketLine("Pérgola & deck", new BigDecimal("2.000"), "m2",
                        new BigDecimal("560"), new BigDecimal("20"), new BigDecimal("1100"))),
                new BigDecimal("1120"), new BigDecimal("20"), new BigDecimal("982.14"), new BigDecimal("117.86"),
                new BigDecimal("12"), new BigDecimal("1100"), "efectivo", false, fel, "paid", null, null);
    }

    @Test
    void elTicketLlevaLosDatosRealesEscapados() {
        String html = SaleTicketService.xhtml(ticket(null, "FAC-000123"));
        assertTrue(html.contains("Madera Viva, S.A."));
        assertTrue(html.contains("NIT 1234567-8 · Tel +502 3050 8989"));
        assertTrue(html.contains("Km 15 &lt;Carretera&gt;"), "la dirección se escapa");
        assertTrue(html.contains("Pérgola &amp; deck"));
        assertTrue(html.contains("Ana López"));
        assertTrue(html.contains("FACTURA") && html.contains("FAC-000123"));
        assertTrue(html.contains("28/11/2026 23:22") || html.contains("27/11/2026 23:22"), "hora de Guatemala");
        assertTrue(html.contains("2 m2 x Q 560.00 · desc. Q 20.00"));
        assertTrue(html.contains("Q 1,100.00"));
        assertTrue(html.contains("EFECTIVO"));
        // Sin certificación no se inventa una autorización.
        assertTrue(html.contains("Documento sin certificación FEL"));
        assertFalse(html.contains("Autorización SAT"));
        assertFalse(html.contains("Stackline · TIENDA"));
    }

    @Test
    void conFelMuestraSuAutorizacion() {
        var fel = new SaleDtos.TicketFel("FACT", "A", "00123", "ABC123", Instant.now(), "2026-1-2");
        String html = SaleTicketService.xhtml(ticket(fel, "FAC-000123"));
        assertTrue(html.contains("Serie A · No. 00123"));
        assertTrue(html.contains("Autorización SAT: ABC123"));
        assertTrue(html.contains("Resol. SAT 2026-1-2"));
    }

    @Test
    void generaUnPdf() throws Exception {
        SaleTicketService s = new SaleTicketService(null, null, null, null, null, null);
        byte[] pdf = s.pdf(ticket(null, "FAC-000123"));
        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void nombreDeArchivoLimpio() {
        assertEquals("Ticket-FAC-000123.pdf", SaleTicketService.archivo(ticket(null, "FAC-000123")));
        assertEquals("Ticket-A_1_2.pdf", SaleTicketService.archivo(ticket(null, "A/1 2")));
        assertEquals("Ticket-venta-7.pdf", SaleTicketService.archivo(ticket(null, null)));
    }

    @Test
    void tiposDeDocumentoYMontos() {
        assertEquals("FACTURA", SaleTicketService.nombreTipo("FACT"));
        assertEquals("NOTA DE CRÉDITO", SaleTicketService.nombreTipo("ncre"));
        assertEquals("Q 1,234.50", SaleTicketService.quetzales(new BigDecimal("1234.5")));
        assertEquals("Q 0.00", SaleTicketService.quetzales(null));
    }

    @Test
    void elRolloCreceConLasLineas() {
        int una = SaleTicketService.altoMm(ticket(null, "X"));
        assertTrue(una >= 150 && una < 200);
    }

    @Test
    void unaVentaAnuladaLoDiceEnElTicket() {
        SaleDtos.Ticket t = ticket(null, "FAC-000123");
        SaleDtos.Ticket anulada = new SaleDtos.Ticket(t.saleId(), t.docType(), t.docNumber(), t.saleDate(), t.companyName(),
                t.legalName(), t.companyNit(), t.companyAddress(), t.companyPhone(), t.companyEmail(), t.branchName(),
                t.cashier(), t.clientName(), t.clientNit(), t.clientEmail(), t.lines(), t.subtotal(), t.discountTotal(),
                t.taxableBase(), t.tax(), t.taxRate(), t.total(), t.paymentMethod(), t.credit(), t.fel(),
                "cancelled", Instant.now(), "Error en el precio");
        String html = SaleTicketService.xhtml(anulada);
        assertTrue(html.contains("*** ANULADA ***"));
        assertTrue(html.contains("Motivo: Error en el precio"));
        assertFalse(SaleTicketService.xhtml(t).contains("ANULADA"));
    }
}
