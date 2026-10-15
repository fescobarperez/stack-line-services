package com.erp_maya.platform.service;

import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.platform.domain.CompanySubscription;
import com.erp_maya.platform.domain.Plan;
import com.erp_maya.platform.dto.PlanDtos.*;
import com.erp_maya.platform.dto.PlatformCompanyDtos.PlanRef;
import com.erp_maya.platform.repository.CompanySubscriptionRepository;
import com.erp_maya.platform.repository.PlanRepository;
import com.erp_maya.platform.security.PlatformException;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/** Catálogo de planes y el plan de cada empresa, desde la consola. */
@Singleton
public class PlatformPlanService {

    private static final Pattern CODE = Pattern.compile("^[a-z0-9_]{2,30}$");

    private final PlanRepository plans;
    private final CompanySubscriptionRepository subscriptions;
    private final CompanyRepository companies;
    private final SubscriptionService subscriptionService;

    public PlatformPlanService(PlanRepository plans, CompanySubscriptionRepository subscriptions,
                               CompanyRepository companies, SubscriptionService subscriptionService) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.companies = companies;
        this.subscriptionService = subscriptionService;
    }

    public static PlanRef ref(Plan p) {
        return new PlanRef(p.getId(), p.getCode(), p.getName());
    }

    // ── Planes ──

    @Transactional
    public List<PlanView> list(boolean money) {
        return plans.findAll().stream()
                .sorted(Comparator.comparing(Plan::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> view(p, money)).toList();
    }

    @Transactional
    public PlanView create(PlanIn in) {
        Map<String, String> f = new LinkedHashMap<>();
        String code = in.code() == null ? "" : in.code().trim();
        if (!CODE.matcher(code).matches()) f.put("code", "Minúsculas, números o guion bajo; 2 a 30 caracteres");
        else if (plans.findByCode(code).isPresent()) {
            throw PlatformException.conflict("CODE_TAKEN", "Ese código ya está en uso", Map.of("code", "Ese código ya está en uso"));
        }
        if (in.name() == null || in.name().isBlank()) f.put("name", "El nombre es obligatorio");
        if (!PlanRules.validPrice(in.price())) f.put("price", "Precio inválido");
        validarModulos(in.modules(), f, "modules");
        validarLimites(in.limits(), f, "limits");
        if (!f.isEmpty()) throw PlatformException.validation(f);
        Plan p = new Plan();
        p.setCode(code);
        p.setName(in.name().trim());
        p.setDescription(texto(in.description()));
        p.setPrice(in.price());
        p.setModules(conBase(in.modules()));
        aplicarLimites(in.limits(), (k, v) -> setLimit(p, k, v));
        p.setActive(true);
        return view(plans.save(p), true);
    }

    @Transactional
    public PlanView update(Long id, PlanIn in) {
        Plan p = plans.findById(id).orElseThrow(() -> PlatformException.notFound("El plan no existe"));
        Map<String, String> f = new LinkedHashMap<>();
        if (in.name() != null && in.name().isBlank()) f.put("name", "El nombre es obligatorio");
        if (in.price() != null && !PlanRules.validPrice(in.price())) f.put("price", "Precio inválido");
        if (in.modules() != null) validarModulos(in.modules(), f, "modules");
        if (in.limits() != null) validarLimites(in.limits(), f, "limits");
        if (!f.isEmpty()) throw PlatformException.validation(f);
        if (in.name() != null) p.setName(in.name().trim());
        if (in.description() != null) p.setDescription(texto(in.description()));
        if (in.price() != null) p.setPrice(in.price());
        if (in.modules() != null) p.setModules(conBase(in.modules()));
        if (in.limits() != null) aplicarLimites(in.limits(), (k, v) -> setLimit(p, k, v));
        if (in.active() != null) p.setActive(in.active());
        plans.update(p);
        // Cambia lo que pueden usar todas las empresas del plan.
        subscriptionService.invalidateAll();
        return view(p, true);
    }

    // ── Plan de una empresa ──

    @Transactional
    public SubscriptionView get(Long companyId, boolean money) {
        companies.findById(companyId).orElseThrow(() -> PlatformException.notFound("La empresa no existe"));
        return subscriptionView(companyId, money);
    }

    @Transactional
    public SubscriptionView save(Long companyId, SubscriptionIn in, String operator) {
        Company c = companies.findById(companyId).orElseThrow(() -> PlatformException.notFound("La empresa no existe"));
        CompanySubscription s = subscriptionService.subscription(companyId);
        Map<String, String> f = new LinkedHashMap<>();
        Plan plan = in.planId() == null ? null : plans.findById(in.planId()).orElse(null);
        // Se puede conservar un plan inactivo que la empresa ya tiene, no pasarse a uno.
        if (plan == null || (!plan.isActive() && !plan.getId().equals(s.getPlanId()))) f.put("planId", "Elige un plan activo");
        OverridesIn ov = in.overrides() == null ? new OverridesIn(List.of(), List.of()) : in.overrides();
        validarModulos(ov.enabled(), f, "overrides.enabled");
        validarModulos(ov.disabled(), f, "overrides.disabled");
        validarLimites(in.limitOverrides(), f, "limitOverrides");
        if (in.priceOverride() != null && !PlanRules.validPrice(in.priceOverride())) f.put("priceOverride", "Precio inválido");
        if (!f.isEmpty()) throw PlatformException.validation(f);

        List<List<String>> norm = PlanRules.normalizeOverrides(plan.getModules(), ov.enabled(), ov.disabled());
        s.setPlanId(plan.getId());
        s.setEnabledModules(new ArrayList<>(norm.get(0)));
        s.setDisabledModules(new ArrayList<>(norm.get(1)));
        PlanRules.LIMIT_KEYS.forEach(k -> setLimit(s, k, null));
        aplicarLimites(in.limitOverrides(), (k, v) -> setLimit(s, k, v));
        s.setPriceOverride(in.priceOverride());
        s.setUpdatedBy(operator);
        subscriptions.update(s);
        // companies.plan se mantiene igual al código del plan (lo leen reportes viejos).
        c.setPlan(plan.getCode());
        companies.update(c);
        subscriptionService.invalidate(companyId);
        return subscriptionView(companyId, true);
    }

    /** Alta de empresa: su suscripción nace con el plan elegido y sin excepciones. */
    public void createFor(Long companyId, Plan plan, String operator) {
        CompanySubscription s = new CompanySubscription();
        s.setCompanyId(companyId);
        s.setPlanId(plan.getId());
        s.setUpdatedBy(operator);
        subscriptions.save(s);
    }

    // ── Vistas ──

    private SubscriptionView subscriptionView(Long companyId, boolean money) {
        SubscriptionService.Effective e = subscriptionService.effective(companyId);
        CompanySubscription s = e.subscription();
        Map<String, Integer> propios = new LinkedHashMap<>();
        SubscriptionService.limitsOf(s).forEach((k, v) -> { if (v != null) propios.put(k, v); });
        Usage usage = new Usage(subscriptionService.activeUsers(companyId), subscriptionService.activeBranches(companyId),
                subscriptionService.felThisMonth(companyId), null);
        return new SubscriptionView(ref(e.plan()), new Overrides(s.getEnabledModules(), s.getDisabledModules()), propios,
                money ? s.getPriceOverride() : null,
                new Effective(money ? e.price() : null, e.modules(), e.limits()), usage);
    }

    private PlanView view(Plan p, boolean money) {
        return new PlanView(p.getId(), p.getCode(), p.getName(), p.getDescription(), money ? p.getPrice() : null,
                p.getModules(), SubscriptionService.limitsOf(p), p.isActive(), subscriptions.countByPlanId(p.getId()));
    }

    private static List<String> conBase(@Nullable List<String> mods) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(ModuleCatalog.BASE);
        if (mods != null) set.addAll(mods);
        return ModuleCatalog.CODES.stream().filter(set::contains).toList();
    }

    private static void validarModulos(@Nullable List<String> mods, Map<String, String> f, String campo) {
        List<String> raros = PlanRules.unknownModules(mods);
        if (!raros.isEmpty()) f.put(campo, "Módulos desconocidos: " + String.join(", ", raros));
    }

    private static void validarLimites(@Nullable Map<String, Object> lim, Map<String, String> f, String prefijo) {
        if (lim == null) return;
        lim.forEach((k, v) -> {
            if (!PlanRules.LIMIT_KEYS.contains(k)) f.put(prefijo + "." + k, "Límite desconocido");
            else if (!PlanRules.validLimit(v)) f.put(prefijo + "." + k, "Debe ser un entero mayor o igual a 0");
        });
    }

    private static void aplicarLimites(@Nullable Map<String, Object> lim, BiConsumer<String, Integer> set) {
        if (lim == null) return;
        lim.forEach((k, v) -> set.accept(k, v == null ? null : ((Number) v).intValue()));
    }

    private static void setLimit(Plan p, String k, @Nullable Integer v) {
        switch (k) {
            case "maxUsers" -> p.setMaxUsers(v);
            case "maxBranches" -> p.setMaxBranches(v);
            case "maxFelPerMonth" -> p.setMaxFelPerMonth(v);
            case "maxAiMessagesPerMonth" -> p.setMaxAiMessagesPerMonth(v);
            default -> { }
        }
    }

    private static void setLimit(CompanySubscription s, String k, @Nullable Integer v) {
        switch (k) {
            case "maxUsers" -> s.setMaxUsers(v);
            case "maxBranches" -> s.setMaxBranches(v);
            case "maxFelPerMonth" -> s.setMaxFelPerMonth(v);
            case "maxAiMessagesPerMonth" -> s.setMaxAiMessagesPerMonth(v);
            default -> { }
        }
    }

    private static @Nullable String texto(@Nullable String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
