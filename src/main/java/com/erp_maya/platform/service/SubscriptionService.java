package com.erp_maya.platform.service;

import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.fel.repository.FelDocumentRepository;
import com.erp_maya.platform.domain.CompanySubscription;
import com.erp_maya.platform.domain.Plan;
import com.erp_maya.platform.repository.CompanySubscriptionRepository;
import com.erp_maya.platform.repository.PlanRepository;
import com.erp_maya.security.repository.UserRepository;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El plan efectivo de una empresa (módulos, límites, precio) y el uso actual.
 * Lo usan la consola, el filtro de módulos y los servicios que crean usuarios,
 * sucursales o documentos FEL.
 */
@Singleton
public class SubscriptionService {

    public static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    static final Duration TTL = Duration.ofSeconds(30);
    /** Plan para una empresa sin suscripción (p. ej. la del seed de desarrollo): no se le quita nada. */
    static final String DEFAULT_PLAN = "completo";

    public record Effective(Plan plan, CompanySubscription subscription, List<String> modules,
                            Map<String, Integer> limits, BigDecimal price) {}

    private record Cached(List<String> modules, Instant at) {}

    private final PlanRepository plans;
    private final CompanySubscriptionRepository subscriptions;
    private final UserRepository users;
    private final BranchRepository branches;
    private final FelDocumentRepository fel;
    private final ConcurrentHashMap<Long, Cached> modulesCache = new ConcurrentHashMap<>();

    public SubscriptionService(PlanRepository plans, CompanySubscriptionRepository subscriptions,
                               UserRepository users, BranchRepository branches, FelDocumentRepository fel) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.users = users;
        this.branches = branches;
        this.fel = fel;
    }

    public static Map<String, Integer> limitsOf(Plan p) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("maxUsers", p.getMaxUsers());
        m.put("maxBranches", p.getMaxBranches());
        m.put("maxFelPerMonth", p.getMaxFelPerMonth());
        m.put("maxAiMessagesPerMonth", p.getMaxAiMessagesPerMonth());
        return m;
    }

    public static Map<String, Integer> limitsOf(CompanySubscription s) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("maxUsers", s.getMaxUsers());
        m.put("maxBranches", s.getMaxBranches());
        m.put("maxFelPerMonth", s.getMaxFelPerMonth());
        m.put("maxAiMessagesPerMonth", s.getMaxAiMessagesPerMonth());
        return m;
    }

    /** La suscripción de la empresa; si no tiene, se crea en el plan por defecto. */
    @Transactional
    public CompanySubscription subscription(Long companyId) {
        return subscriptions.findById(companyId).orElseGet(() -> {
            Plan p = plans.findByCode(DEFAULT_PLAN).orElseGet(() -> plans.findAll().iterator().next());
            CompanySubscription s = new CompanySubscription();
            s.setCompanyId(companyId);
            s.setPlanId(p.getId());
            s.setUpdatedBy("Sistema");
            return subscriptions.save(s);
        });
    }

    @Transactional
    public Effective effective(Long companyId) {
        CompanySubscription s = subscription(companyId);
        Plan p = plans.findById(s.getPlanId()).orElseThrow();
        List<String> mods = PlanRules.effectiveModules(p.getModules(), s.getEnabledModules(), s.getDisabledModules());
        Map<String, Integer> lim = PlanRules.effectiveLimits(limitsOf(p), limitsOf(s));
        BigDecimal price = s.getPriceOverride() != null ? s.getPriceOverride() : p.getPrice();
        return new Effective(p, s, mods, lim, price);
    }

    /** Módulos efectivos con caché corto: los pide el filtro en cada request. */
    @Transactional
    public List<String> modules(Long companyId) {
        Instant now = Instant.now();
        Cached c = modulesCache.get(companyId);
        if (c == null || now.isAfter(c.at().plus(TTL))) {
            c = new Cached(effective(companyId).modules(), now);
            modulesCache.put(companyId, c);
        }
        return c.modules();
    }

    public void invalidate(Long companyId) { modulesCache.remove(companyId); }

    public void invalidateAll() { modulesCache.clear(); }

    // ── Uso ──

    public static Instant startOfMonth(Instant now) {
        LocalDate d = now.atZone(GUATEMALA).toLocalDate().withDayOfMonth(1);
        return d.atStartOfDay(GUATEMALA).toInstant();
    }

    @Transactional
    public long activeUsers(Long companyId) {
        return users.findByCompanyId(companyId).stream().filter(u -> "active".equals(u.getStatus())).count();
    }

    @Transactional
    public long activeBranches(Long companyId) {
        return branches.findByCompanyId(companyId).stream().filter(b -> "active".equals(b.getStatus())).count();
    }

    @Transactional
    public long felThisMonth(Long companyId) {
        return fel.countByCompanyIdAndCertifiedAtGreaterThanEquals(companyId, startOfMonth(Instant.now()));
    }

    /**
     * Lanza 409 si sumar uno más pasaría el límite. `used` es lo que ya hay
     * (sin contar lo que se va a crear).
     */
    public static void check(String key, long used, @Nullable Integer limit, String what) {
        if (limit != null && used + 1 > limit) {
            throw new LimitReachedException(key, limit,
                    "Tu plan permite " + limit + " " + what + ". Para más, contacta a Stackline.");
        }
    }

    @Transactional
    public void checkUsers(Long companyId) {
        check("maxUsers", activeUsers(companyId), effective(companyId).limits().get("maxUsers"), "usuarios activos");
    }

    @Transactional
    public void checkBranches(Long companyId) {
        check("maxBranches", activeBranches(companyId), effective(companyId).limits().get("maxBranches"), "sucursales activas");
    }

    @Transactional
    public void checkFel(Long companyId) {
        check("maxFelPerMonth", felThisMonth(companyId), effective(companyId).limits().get("maxFelPerMonth"), "documentos FEL al mes");
    }
}
