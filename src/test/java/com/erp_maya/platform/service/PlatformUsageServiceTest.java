package com.erp_maya.platform.service;

import com.erp_maya.platform.security.PlatformException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de uso y costos; las mismas que src/lib/usage.js de la consola. */
class PlatformUsageServiceTest {

    @Test
    void soloUnaEmpresaActivaFactura() {
        assertEquals(0, new BigDecimal("1200").compareTo(PlatformUsageService.revenue("active", new BigDecimal("1200"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(PlatformUsageService.revenue("trial", new BigDecimal("1200"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(PlatformUsageService.revenue("suspended", new BigDecimal("1200"))));
    }

    @Test
    void elMargenSeCalculaEnQuetzalesYSinIngresoNoHayPorcentaje() {
        Map<String, Object> m = PlatformUsageService.margin(new BigDecimal("1000"), new BigDecimal("1.27"), new BigDecimal("7.75"));
        assertEquals(new BigDecimal("9.84"), m.get("costGtq"));
        assertEquals(new BigDecimal("990.16"), m.get("marginGtq"));
        assertEquals(new BigDecimal("99.0"), m.get("marginPct"));
        Map<String, Object> sin = PlatformUsageService.margin(BigDecimal.ZERO, new BigDecimal("2"), new BigDecimal("7.75"));
        assertEquals(new BigDecimal("-15.50"), sin.get("marginGtq"));
        assertNull(sin.get("marginPct"));
    }

    @Test
    void elMesVaEnHoraDeGuatemalaYNoPuedeSerFuturo() {
        Instant ahora = Instant.parse("2026-11-01T03:00:00Z"); // 31 oct, 21:00 en Guatemala
        assertEquals("2026-10", PlatformUsageService.mes(null, ahora).key());
        assertTrue(PlatformUsageService.mes("2026-10", ahora).partial());
        assertFalse(PlatformUsageService.mes("2026-09", ahora).partial());
        assertThrows(PlatformException.class, () -> PlatformUsageService.mes("2026-11", ahora));
        assertThrows(PlatformException.class, () -> PlatformUsageService.mes("2026-13", ahora));
    }

    @Test
    void sinDineroOcultaLasLlavesEnCualquierNivel() {
        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("turns", 5);
        ai.put("costUsd", new BigDecimal("1"));
        ai.put("byModel", List.of(Map.of("model", "gpt-4o", "costUsd", new BigDecimal("1"))));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("priceGtq", new BigDecimal("1200"));
        r.put("marginPct", new BigDecimal("99"));
        r.put("ai", ai);
        Map<String, Object> s = PlatformUsageService.sinDinero(r);
        assertNull(s.get("priceGtq"));
        assertNull(s.get("marginPct"));
        @SuppressWarnings("unchecked") Map<String, Object> a = (Map<String, Object>) s.get("ai");
        assertEquals(5, a.get("turns"));
        assertNull(a.get("costUsd"));
        @SuppressWarnings("unchecked") Map<String, Object> fila = (Map<String, Object>) ((List<?>) a.get("byModel")).get(0);
        assertEquals("gpt-4o", fila.get("model"));
        assertNull(fila.get("costUsd"));
    }

    @Test
    void niveles() {
        assertEquals("near", PlatformUsageService.limit("maxAiMessagesPerMonth", 96, 120).get("level"));
        assertEquals("over", PlatformUsageService.limit("maxFelPerMonth", 101, 100).get("level"));
        assertEquals("ok", PlatformUsageService.limit("maxUsers", 50, null).get("level"));
    }
}
