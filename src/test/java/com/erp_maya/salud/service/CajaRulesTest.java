package com.erp_maya.salud.service;

import com.erp_maya.salud.domain.Appointment;
import com.erp_maya.salud.domain.PatientPackage;
import com.erp_maya.salud.dto.CajaDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reparto del cobro, paquetes, avance de planes y números de los reportes. */
class CajaRulesTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 2);

    private static BigDecimal q(String v) {
        return new BigDecimal(v);
    }

    // ── Reparto del cobro ────────────────────────────────────────────────

    @Test
    void sinAnticipoSeFacturaTodo() {
        CajaRules.Reparto r = CajaRules.repartir(q("350"), q("50"), q("500"), false);
        assertEquals(q("300.00"), r.total());
        assertEquals(q("0.00"), r.credito());
        assertEquals(q("300.00"), r.caja());
        assertEquals(CajaRules.VENTA, r.metodo());
    }

    @Test
    void elAnticipoCubreTodoSiAlcanza() {
        CajaRules.Reparto r = CajaRules.repartir(q("300"), null, q("500"), true);
        assertEquals(q("300.00"), r.credito());
        assertEquals(q("0.00"), r.caja());
        assertEquals(CajaRules.ANTICIPO, r.metodo());
    }

    @Test
    void siElAnticipoNoAlcanzaElRestoSeFactura() {
        CajaRules.Reparto r = CajaRules.repartir(q("300"), null, q("120.50"), true);
        assertEquals(q("120.50"), r.credito());
        assertEquals(q("179.50"), r.caja());
        assertEquals(CajaRules.MIXTO, r.metodo());
    }

    @Test
    void sinSaldoUsarAnticipoNoCambiaNada() {
        CajaRules.Reparto r = CajaRules.repartir(q("300"), null, BigDecimal.ZERO, true);
        assertEquals(CajaRules.VENTA, r.metodo());
        assertEquals(q("300.00"), r.caja());
    }

    @Test
    void elDescuentoNoPuedeSuperarElPrecioNiSerNegativo() {
        assertThrows(IllegalStateException.class, () -> CajaRules.repartir(q("100"), q("150"), BigDecimal.ZERO, false));
        assertThrows(IllegalStateException.class, () -> CajaRules.repartir(q("100"), q("-1"), BigDecimal.ZERO, false));
        assertThrows(IllegalStateException.class, () -> CajaRules.repartir(q("-5"), null, BigDecimal.ZERO, false));
        assertThrows(IllegalStateException.class, () -> CajaRules.repartir(q("10.555"), null, BigDecimal.ZERO, false));
    }

    @Test
    void formasDePago() {
        assertEquals("efectivo", CajaRules.pago(null));
        assertEquals("tarjeta", CajaRules.pago(" Tarjeta "));
        assertThrows(IllegalStateException.class, () -> CajaRules.pago("bitcoin"));
    }

    @Test
    void montoDelAnticipoDebeSerPositivo() {
        assertThrows(IllegalStateException.class, () -> CajaRules.monto(BigDecimal.ZERO, "el monto", false));
        assertThrows(IllegalStateException.class, () -> CajaRules.monto(null, "el monto", false));
        assertEquals(q("250.00"), CajaRules.monto(q("250"), "el monto", false));
    }

    // ── Paquetes ─────────────────────────────────────────────────────────

    private static PatientPackage paquete(long id, long tipo, int total, int usadas, LocalDate vence, String compra) {
        PatientPackage p = new PatientPackage();
        p.setId(id);
        p.setTypeId(tipo);
        p.setSessionsTotal(total);
        p.setSessionsUsed(usadas);
        p.setExpiresOn(vence);
        p.setPurchasedAt(Instant.parse(compra));
        p.setStatus(CajaRules.PK_ACTIVO);
        p.setPrice(q("1500"));
        p.setName("Fisio x" + total);
        return p;
    }

    @Test
    void unPaqueteDeDiezSesionesDescuentaCadaCitaYSeAgota() {
        PatientPackage p = paquete(1, 5, 10, 0, null, "2026-10-01T10:00:00Z");
        for (int i = 0; i < 10; i++) {
            assertTrue(CajaRules.sirve(p, 5L, HOY), "sesión " + (i + 1));
            CajaRules.consumir(p);
        }
        assertEquals(10, p.getSessionsUsed());
        assertEquals(CajaRules.PK_AGOTADO, p.getStatus());
        assertFalse(CajaRules.sirve(p, 5L, HOY));
        assertThrows(IllegalStateException.class, () -> CajaRules.consumir(p));
        // anular un cobro devuelve la sesión y el paquete vuelve a servir
        CajaRules.devolver(p);
        assertEquals(9, p.getSessionsUsed());
        assertEquals(CajaRules.PK_ACTIVO, p.getStatus());
        assertTrue(CajaRules.sirve(p, 5L, HOY));
    }

    @Test
    void elPaqueteSoloSirveParaSuTipoYSinVencer() {
        PatientPackage p = paquete(1, 5, 10, 2, HOY, "2026-10-01T10:00:00Z");
        assertTrue(CajaRules.sirve(p, 5L, HOY));
        assertFalse(CajaRules.sirve(p, 6L, HOY));
        assertFalse(CajaRules.sirve(p, 5L, HOY.plusDays(1)));
        p.setStatus(CajaRules.PK_ANULADO);
        assertFalse(CajaRules.sirve(p, 5L, HOY));
    }

    @Test
    void seUsaPrimeroElQueVenceAntes() {
        PatientPackage sinVencer = paquete(1, 5, 10, 0, null, "2026-09-01T10:00:00Z");
        PatientPackage venceLuego = paquete(2, 5, 10, 0, HOY.plusDays(60), "2026-10-01T10:00:00Z");
        PatientPackage venceYa = paquete(3, 5, 10, 0, HOY.plusDays(5), "2026-10-15T10:00:00Z");
        PatientPackage otroTipo = paquete(4, 9, 10, 0, HOY.plusDays(1), "2026-10-15T10:00:00Z");
        List<PatientPackage> orden = CajaRules.aplicables(List.of(sinVencer, venceLuego, venceYa, otroTipo), 5L, HOY);
        assertEquals(List.of(3L, 2L, 1L), orden.stream().map(PatientPackage::getId).toList());
    }

    @Test
    void valorDeLaSesion() {
        assertEquals(q("150.00"), CajaRules.valorSesion(q("1500"), 10));
        assertEquals(q("333.33"), CajaRules.valorSesion(q("1000"), 3));
    }

    @Test
    void vistaDelPaquete() {
        CajaDtos.PatientPackageView v = CajaRules.toView(paquete(1, 5, 10, 3, null, "2026-10-01T10:00:00Z"), "Fisio", HOY);
        assertEquals(7, v.sessionsLeft());
        assertTrue(v.usable());
    }

    // ── Planes ───────────────────────────────────────────────────────────

    private static Appointment cita(String inicio, String estado) {
        Appointment a = new Appointment();
        a.setStartsAt(OffsetDateTime.parse(inicio).toInstant());
        a.setStatus(estado);
        return a;
    }

    @Test
    void avanceDelPlan() {
        List<Appointment> citas = List.of(
                cita("2026-10-26T09:00:00-06:00", "completada"),
                cita("2026-10-29T09:00:00-06:00", "completada"),
                cita("2026-10-31T09:00:00-06:00", "no_asistio"),
                cita("2026-11-04T09:00:00-06:00", "confirmada"),
                cita("2026-11-01T09:00:00-06:00", "cancelada"));
        CajaRules.Avance a = CajaRules.avance(citas, 10, 3, HOY);
        assertEquals(2, a.hechas());
        assertEquals(1, a.agendadas());
        assertEquals(7, a.faltan());
        // la última en pie es el 4 de noviembre: la siguiente, 3 días después
        assertEquals(LocalDate.of(2026, 11, 7), a.siguiente());
    }

    @Test
    void sinCitasLaSiguienteEsHoyYCompletoNoSugiere() {
        assertEquals(HOY, CajaRules.avance(List.of(), 5, 7, HOY).siguiente());
        List<Appointment> todas = new ArrayList<>();
        for (int i = 0; i < 3; i++) todas.add(cita("2026-10-2" + i + "T09:00:00-06:00", "completada"));
        CajaRules.Avance a = CajaRules.avance(todas, 3, 7, HOY);
        assertEquals(0, a.faltan());
        assertNull(a.siguiente());
    }

    @Test
    void laSugerenciaNuncaQuedaEnElPasado() {
        CajaRules.Avance a = CajaRules.avance(List.of(cita("2026-10-01T09:00:00-06:00", "completada")), 4, 7, HOY);
        assertEquals(HOY, a.siguiente());
    }

    @Test
    void validacionesDeCatalogoYPlan() {
        assertThrows(IllegalStateException.class, () -> CajaRules.sesionesPaquete(0));
        assertThrows(IllegalStateException.class, () -> CajaRules.sesionesPlan(101));
        assertNull(CajaRules.vigencia(0));
        assertNull(CajaRules.intervalo(null));
        assertThrows(IllegalStateException.class, () -> CajaRules.intervalo(91));
        assertEquals("peor", CajaRules.feeling(" Peor "));
        assertThrows(IllegalStateException.class, () -> CajaRules.feeling("regular"));
        assertThrows(IllegalStateException.class, () -> CajaRules.controlReminderDays(15));
    }

    // ── Reportes ─────────────────────────────────────────────────────────

    private static CajaRules.CitaHecho c(int dia, long prof, String estado) {
        return new CajaRules.CitaHecho(LocalDate.of(2026, 11, dia), prof, prof == 1 ? "Dra. Pérez" : "Dr. Gómez", estado);
    }

    @Test
    void tasaDeInasistencia() {
        assertEquals(25.0, CajaRules.tasaInasistencia(3, 1));
        assertEquals(33.3, CajaRules.tasaInasistencia(2, 1));
        assertNull(CajaRules.tasaInasistencia(0, 0));
    }

    @Test
    void citasPorDiaYPorProfesional() {
        List<CajaRules.CitaHecho> citas = List.of(
                c(2, 1, "completada"), c(2, 1, "no_asistio"), c(2, 2, "completada"),
                c(3, 1, "cancelada"), c(3, 1, "completada"), c(3, 1, "confirmada"));
        List<CajaDtos.DayRow> dias = CajaRules.porDia(citas);
        assertEquals(2, dias.size());
        assertEquals(LocalDate.of(2026, 11, 2), dias.get(0).date());
        assertEquals(3, dias.get(0).appointments());
        assertEquals(1, dias.get(0).noShow());
        assertEquals(1, dias.get(1).cancelled());

        List<CajaDtos.ProfessionalRow> profs = CajaRules.porProfesional(citas);
        assertEquals("Dra. Pérez", profs.get(0).name());
        assertEquals(5, profs.get(0).appointments());
        assertEquals(2, profs.get(0).completed());
        assertEquals(33.3, profs.get(0).noShowRate());
        assertEquals(0.0, profs.get(1).noShowRate());
    }

    @Test
    void ingresosPorServicioYTotales() {
        List<CajaRules.CobroHecho> cobros = List.of(
                new CajaRules.CobroHecho(5L, "Fisioterapia", CajaRules.PAQUETE, q("150"), BigDecimal.ZERO, BigDecimal.ZERO),
                new CajaRules.CobroHecho(5L, "Fisioterapia", CajaRules.PAQUETE, q("150"), BigDecimal.ZERO, BigDecimal.ZERO),
                new CajaRules.CobroHecho(3L, "Consulta", CajaRules.VENTA, q("350"), q("350"), BigDecimal.ZERO),
                new CajaRules.CobroHecho(3L, "Consulta", CajaRules.MIXTO, q("350"), q("200"), q("150")),
                new CajaRules.CobroHecho(3L, "Consulta", CajaRules.CORTESIA, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        List<CajaDtos.ServiceRow> s = CajaRules.porServicio(cobros);
        assertEquals("Consulta", s.get(0).name());
        assertEquals(3, s.get(0).charges());
        assertEquals(1, s.get(0).courtesies());
        assertEquals(q("700.00"), s.get(0).serviceValue());
        assertEquals(q("550.00"), s.get(0).cash());
        assertEquals(q("150.00"), s.get(0).credit());
        assertEquals(2, s.get(1).packageSessions());
        assertEquals(q("300.00"), s.get(1).serviceValue());

        CajaDtos.Totals t = CajaRules.totales(List.of(c(2, 1, "completada"), c(2, 1, "no_asistio")), cobros,
                q("1500"), q("500"));
        assertEquals(50.0, t.noShowRate());
        assertEquals(q("1000.00"), t.serviceValue());
        assertEquals(q("550.00"), t.chargesCash());
        // en caja: cobros de citas + paquetes vendidos + anticipos recibidos
        assertEquals(q("2550.00"), t.cashIn());
    }

    @Test
    void periodoDelReporte() {
        CajaRules.checkPeriodo(HOY, HOY);
        assertThrows(IllegalStateException.class, () -> CajaRules.checkPeriodo(HOY, HOY.minusDays(1)));
        assertThrows(IllegalStateException.class, () -> CajaRules.checkPeriodo(HOY, HOY.plusDays(400)));
    }
}
