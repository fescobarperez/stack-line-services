package com.erp_maya.platform.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las mismas reglas que src/lib/plans.js de la consola. */
class PlanRulesTest {

    private static final List<String> COMERCIAL = List.of("base", "pos", "fel", "quotes", "commercial");

    @Test
    void excepcionesEnSuFormaMinima() {
        List<List<String>> o = PlanRules.normalizeOverrides(COMERCIAL, List.of("ai", "pos", "base", "ai"), List.of("commercial", "payroll", "base"));
        assertEquals(List.of("ai"), o.get(0));
        assertEquals(List.of("commercial"), o.get(1));
    }

    @Test
    void modulosEfectivosEnElOrdenDelCatalogo() {
        assertEquals(List.of("base", "pos", "fel", "quotes", "ai"),
                PlanRules.effectiveModules(COMERCIAL, List.of("ai"), List.of("commercial")));
        assertTrue(PlanRules.effectiveModules(List.of(), null, List.of("base")).contains("base"));
    }

    @Test
    void limitePropioGanaAlDelPlan() {
        Map<String, Integer> plan = new HashMap<>();
        plan.put("maxUsers", 15);
        plan.put("maxBranches", 3);
        Map<String, Integer> propio = new HashMap<>();
        propio.put("maxUsers", 20);
        Map<String, Integer> e = PlanRules.effectiveLimits(plan, propio);
        assertEquals(20, e.get("maxUsers"));
        assertEquals(3, e.get("maxBranches"));
        assertNull(e.get("maxFelPerMonth"));
    }

    @Test
    void validaciones() {
        assertTrue(PlanRules.validLimit(null));
        assertTrue(PlanRules.validLimit(0));
        assertFalse(PlanRules.validLimit(-1));
        assertFalse(PlanRules.validLimit(2.5));
        assertFalse(PlanRules.validLimit("5"));
        assertTrue(PlanRules.validPrice(new BigDecimal("199.50")));
        assertFalse(PlanRules.validPrice(new BigDecimal("10.555")));
        assertFalse(PlanRules.validPrice(new BigDecimal("-1")));
        assertFalse(PlanRules.validPrice(null));
        assertEquals(List.of("nada"), PlanRules.unknownModules(List.of("pos", "nada")));
    }

    @Test
    void nivelDeUso() {
        assertEquals("ok", PlanRules.usageLevel(100, null));
        assertEquals("near", PlanRules.usageLevel(4, 5));
        assertEquals("over", PlanRules.usageLevel(6, 5));
    }

    @Test
    void rutasPorModulo() {
        assertEquals("quotes", ModuleCatalog.moduleFor("/api/quotes/12/pdf"));
        assertEquals("fel", ModuleCatalog.moduleFor("/api/fel"));
        assertEquals("ai", ModuleCatalog.moduleFor("/api/agent/turn"));
        assertNull(ModuleCatalog.moduleFor("/api/sales"));
        assertNull(ModuleCatalog.moduleFor("/api/accountsx"));
        assertEquals("accounting", ModuleCatalog.moduleFor("/api/accounts/3"));
    }

    @Test
    void elLimiteSeRevisaAntesDeCrear() {
        SubscriptionService.check("maxUsers", 4, 5, "usuarios");
        SubscriptionService.check("maxUsers", 100, null, "usuarios");
        LimitReachedException e = assertThrows(LimitReachedException.class, () -> SubscriptionService.check("maxUsers", 5, 5, "usuarios"));
        assertEquals("maxUsers", e.getLimit());
        assertEquals(5, e.getValue());
    }

    @Test
    void elMesEmpiezaEnHoraDeGuatemala() {
        // 1 de noviembre 03:00 UTC = 31 de octubre 21:00 en Guatemala → el mes es octubre.
        assertEquals(Instant.parse("2026-10-01T06:00:00Z"), SubscriptionService.startOfMonth(Instant.parse("2026-11-01T03:00:00Z")));
        assertEquals(Instant.parse("2026-11-01T06:00:00Z"), SubscriptionService.startOfMonth(Instant.parse("2026-11-01T07:00:00Z")));
    }
}
