package com.erp_maya.platform.service;

import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.fel.repository.FelDocumentRepository;
import com.erp_maya.platform.domain.CompanyStatusChange;
import com.erp_maya.platform.repository.CompanyStatusChangeRepository;
import com.erp_maya.platform.security.PlatformException;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Uso y costos de la consola (API.md · «Uso y costos»): junta lo del ERP
 * (estado, precio, FEL, usuarios, sucursales) con el consumo de IA que
 * reporta agents-services ({@code /v1/admin/usage}).
 *
 * Reglas, las mismas de {@code src/lib/usage.js} de la consola:
 * - Ingreso = precio vigente si la empresa estaba {@code active} al cierre del
 *   mes (o hoy, si está en curso); prueba, suspendida o cancelada = 0. El
 *   precio es el de hoy: aún no hay historial de precios ni facturación.
 * - Margen = ingreso − costo × tipo de cambio; {@code marginPct} nulo sin ingreso.
 * - Usuarios y sucursales solo en el mes en curso (son una foto).
 * - El dinero solo lo ve owner: para los demás roles esas llaves van en null.
 */
@Singleton
public class PlatformUsageService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUsageService.class);
    private static final int HISTORIAL = 6;
    /** Llaves de dinero que se ocultan a quien no es owner (en cualquier nivel). */
    private static final Set<String> DINERO = Set.of("priceGtq", "revenueGtq", "costUsd", "costGtq", "marginGtq", "marginPct");

    private final CompanyRepository companies;
    private final CompanyStatusChangeRepository history;
    private final SubscriptionService subscriptions;
    private final FelDocumentRepository fel;
    private final AgentAdminProxy agents;
    private final BigDecimal fx;

    public PlatformUsageService(CompanyRepository companies, CompanyStatusChangeRepository history,
                                SubscriptionService subscriptions, FelDocumentRepository fel, AgentAdminProxy agents,
                                @Value("${erp.platform.fx.usd-to-gtq:7.75}") BigDecimal fx) {
        this.companies = companies;
        this.history = history;
        this.subscriptions = subscriptions;
        this.fel = fel;
        this.agents = agents;
        this.fx = fx;
    }

    // ── mes ──

    /** Un mes en hora de Guatemala, {@code [inicio, fin)}. */
    record Mes(YearMonth ym, LocalDate hoy) {
        String key() { return ym.toString(); }
        boolean partial() { return ym.equals(YearMonth.from(hoy)); }
        Instant inicio() { return ym.atDay(1).atStartOfDay(SubscriptionService.GUATEMALA).toInstant(); }
        Instant fin() { return ym.plusMonths(1).atDay(1).atStartOfDay(SubscriptionService.GUATEMALA).toInstant(); }
    }

    static Mes mes(@Nullable String texto, Instant ahora) {
        LocalDate hoy = LocalDate.ofInstant(ahora, SubscriptionService.GUATEMALA);
        YearMonth actual = YearMonth.from(hoy);
        if (texto == null || texto.isBlank()) return new Mes(actual, hoy);
        if (!texto.matches("^\\d{4}-(0[1-9]|1[0-2])$")) throw PlatformException.validation(Map.of("month", "Mes inválido (YYYY-MM)"));
        YearMonth ym = YearMonth.parse(texto);
        if (ym.isAfter(actual)) throw PlatformException.validation(Map.of("month", "El mes no puede ser futuro"));
        return new Mes(ym, hoy);
    }

    // ── reglas puras (las prueba PlatformUsageServiceTest) ──

    static BigDecimal revenue(String status, @Nullable BigDecimal price) {
        return "active".equals(status) && price != null ? price : BigDecimal.ZERO;
    }

    /** {costGtq, marginGtq, marginPct}; marginPct nulo sin ingreso. */
    static Map<String, Object> margin(BigDecimal revenue, BigDecimal costUsd, BigDecimal fx) {
        BigDecimal costGtq = costUsd.multiply(fx).setScale(2, RoundingMode.HALF_UP);
        BigDecimal margen = revenue.subtract(costGtq).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("costGtq", costGtq);
        m.put("marginGtq", margen);
        m.put("marginPct", revenue.signum() > 0
                ? margen.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP) : null);
        return m;
    }

    /** Estado al cierre del mes: el último cambio antes del fin; en curso, el de hoy. */
    static String statusAt(Company c, List<CompanyStatusChange> cambios, Mes m) {
        if (m.partial()) return c.getStatus();
        String estado = null;
        for (CompanyStatusChange h : cambios) {
            if (h.getCreatedAt() != null && h.getCreatedAt().isBefore(m.fin())) estado = h.getStatus();
        }
        return estado != null ? estado : c.getStatus();
    }

    static Map<String, Object> limit(String key, long used, @Nullable Integer limit) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("key", key);
        l.put("used", used);
        l.put("limit", limit);
        l.put("level", PlanRules.usageLevel(used, limit));
        return l;
    }

    // ── resumen de todas las empresas ──

    @Transactional
    public Map<String, Object> overview(@Nullable String month, boolean money) {
        Mes m = mes(month, Instant.now());
        Map<Long, Map<String, Object>> ia = new LinkedHashMap<>();
        for (Object o : lista(agents.getJson("/v1/admin/usage?month=" + m.key()).get("tenants"))) {
            if (o instanceof Map<?, ?> t) ia.put(lng(t.get("tenantId")), castMap(t));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        long aiTurns = 0, felCount = 0;
        BigDecimal ingreso = BigDecimal.ZERO, costo = BigDecimal.ZERO;
        for (Company c : StreamSupport.stream(companies.findAll().spliterator(), false)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList()) {
            if (c.getCreatedAt() != null && !c.getCreatedAt().isBefore(m.fin())) continue; // no existía
            String estado = statusAt(c, history.findByCompanyIdOrderByCreatedAtAsc(c.getId()), m);
            SubscriptionService.Effective e = subscriptions.effective(c.getId());
            Map<String, Object> suya = ia.getOrDefault(c.getId(), Map.of());
            long turnos = lng(suya.get("turns"));
            BigDecimal costoUsd = dec(suya.get("costUsd"));
            long fels = fel.countByCompanyIdAndCertifiedAtGreaterThanEqualsAndCertifiedAtLessThan(c.getId(), m.inicio(), m.fin());
            BigDecimal rev = revenue(estado, e.price());
            if ("cancelled".equals(estado) && turnos == 0 && fels == 0 && costoUsd.signum() == 0 && rev.signum() == 0) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("company", Map.of("id", c.getId(), "code", nz(c.getCode()), "name", c.getName(), "status", estado));
            item.put("plan", planRef(e));
            item.put("priceGtq", e.price());
            item.put("revenueGtq", rev);
            item.put("costUsd", costoUsd);
            item.putAll(margin(rev, costoUsd, fx));
            item.put("aiTurns", turnos);
            item.put("felCount", fels);
            item.put("limits", limites(c.getId(), e, m, turnos, fels));
            items.add(item);

            aiTurns += turnos;
            felCount += fels;
            ingreso = ingreso.add(rev);
            costo = costo.add(costoUsd);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("companies", items.size());
        totals.put("aiTurns", aiTurns);
        totals.put("felCount", felCount);
        totals.put("revenueGtq", ingreso);
        totals.put("costUsd", costo);
        totals.putAll(margin(ingreso, costo, fx));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("month", m.key());
        r.put("partial", m.partial());
        r.put("fx", Map.of("usdToGtq", fx));
        r.put("totals", totals);
        r.put("items", items);
        return money ? r : sinDinero(r);
    }

    // ── detalle de una empresa ──

    @Transactional
    public Map<String, Object> company(Long companyId, @Nullable String month, boolean money) {
        Company c = companies.findById(companyId).orElseThrow(() -> PlatformException.notFound("La empresa no existe"));
        Mes m = mes(month, Instant.now());
        List<CompanyStatusChange> cambios = history.findByCompanyIdOrderByCreatedAtAsc(companyId);
        SubscriptionService.Effective e = subscriptions.effective(companyId);
        Map<String, Object> ai = agents.getJson("/v1/admin/usage/tenants/" + companyId + "?month=" + m.key() + "&months=" + HISTORIAL);

        String estado = statusAt(c, cambios, m);
        long turnos = lng(ai.get("turns"));
        BigDecimal costoUsd = dec(ai.get("costUsd"));
        long fels = fel.countByCompanyIdAndCertifiedAtGreaterThanEqualsAndCertifiedAtLessThan(companyId, m.inicio(), m.fin());
        BigDecimal rev = revenue(estado, e.price());

        Map<String, Object> bloqueIa = new LinkedHashMap<>();
        for (String k : List.of("turns", "calls", "tokensIn", "tokensCached", "tokensOut", "costUsd", "images", "byModel", "byKind", "byChannel")) {
            bloqueIa.put(k, ai.get(k));
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("month", m.key());
        r.put("partial", m.partial());
        r.put("fx", Map.of("usdToGtq", fx));
        r.put("status", estado);
        r.put("priceGtq", e.price());
        r.put("priceSource", e.subscription().getPriceOverride() != null ? "override" : "plan");
        r.put("revenueGtq", rev);
        r.put("costUsd", costoUsd);
        r.putAll(margin(rev, costoUsd, fx));
        r.put("ai", bloqueIa);
        r.put("whatsapp", Map.of("templates", 0, "costUsd", BigDecimal.ZERO));
        r.put("fel", Map.of("count", fels));
        r.put("daily", ai.getOrDefault("daily", List.of()));
        r.put("limits", limites(companyId, e, m, turnos, fels));
        r.put("history", historial(c, cambios, e, ai, m));
        return money ? r : sinDinero(r);
    }

    /** Mensajes de IA del mes en curso (para «Plan y módulos»); nulo si agents-services no responde. */
    public @Nullable Long aiTurnsThisMonth(Long companyId) {
        try {
            Mes m = mes(null, Instant.now());
            for (Object o : lista(agents.getJson("/v1/admin/usage?month=" + m.key()).get("tenants"))) {
                if (o instanceof Map<?, ?> t && lng(t.get("tenantId")) == companyId) return lng(t.get("turns"));
            }
            return 0L;
        } catch (RuntimeException e) {
            log.debug("sin uso de IA para la empresa {}: {}", companyId, e.getMessage());
            return null;
        }
    }

    // ── partes ──

    private List<Map<String, Object>> limites(Long companyId, SubscriptionService.Effective e, Mes m, long turnos, long fels) {
        List<Map<String, Object>> l = new ArrayList<>();
        if (m.partial()) {
            l.add(limit("maxUsers", subscriptions.activeUsers(companyId), e.limits().get("maxUsers")));
            l.add(limit("maxBranches", subscriptions.activeBranches(companyId), e.limits().get("maxBranches")));
        }
        l.add(limit("maxFelPerMonth", fels, e.limits().get("maxFelPerMonth")));
        l.add(limit("maxAiMessagesPerMonth", turnos, e.limits().get("maxAiMessagesPerMonth")));
        return l;
    }

    /** Los últimos meses en que la empresa existía, del más reciente al más antiguo. */
    private List<Map<String, Object>> historial(Company c, List<CompanyStatusChange> cambios, SubscriptionService.Effective e,
                                                Map<String, Object> ai, Mes m) {
        Map<String, Map<?, ?>> iaMes = new LinkedHashMap<>();
        for (Object o : lista(ai.get("history"))) {
            if (o instanceof Map<?, ?> h) iaMes.put(String.valueOf(h.get("month")), h);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < HISTORIAL; i++) {
            Mes h = new Mes(m.ym().minusMonths(i), m.hoy());
            if (c.getCreatedAt() != null && !c.getCreatedAt().isBefore(h.fin())) break;
            Map<?, ?> suyo = iaMes.getOrDefault(h.key(), Map.of());
            BigDecimal costoUsd = dec(suyo.get("costUsd"));
            BigDecimal rev = revenue(statusAt(c, cambios, h), e.price());
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("month", h.key());
            fila.put("aiTurns", lng(suyo.get("turns")));
            fila.put("felCount", fel.countByCompanyIdAndCertifiedAtGreaterThanEqualsAndCertifiedAtLessThan(c.getId(), h.inicio(), h.fin()));
            fila.put("revenueGtq", rev);
            fila.put("costUsd", costoUsd);
            fila.putAll(margin(rev, costoUsd, fx));
            out.add(fila);
        }
        return out;
    }

    private static Map<String, Object> planRef(SubscriptionService.Effective e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", e.plan().getId());
        p.put("code", e.plan().getCode());
        p.put("name", e.plan().getName());
        return p;
    }

    /** Copia sin dinero: esas llaves quedan en null en cualquier nivel. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> sinDinero(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, DINERO.contains(k) ? null : limpiar(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object limpiar(Object v) {
        if (v instanceof Map<?, ?> mm) return sinDinero((Map<String, Object>) mm);
        if (v instanceof List<?> l) return l.stream().map(PlatformUsageService::limpiar).toList();
        return v;
    }

    // ── lectura del JSON de agents-services ──

    private static List<?> lista(Object o) { return o instanceof List<?> l ? l : List.of(); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) { return (Map<String, Object>) m; }

    static long lng(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
        return 0;
    }

    static BigDecimal dec(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        if (o instanceof String s) try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
        return BigDecimal.ZERO;
    }

    private static String nz(@Nullable String s) { return s == null ? "" : s; }
}
