package com.erp_maya.platform.service;

import io.micronaut.core.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La misma regla que src/lib/plans.js de la consola:
 *   módulos efectivos = base ∪ (plan ∪ activados) − desactivados
 *   límite efectivo   = propio ?? del plan   (null = ilimitado)
 */
public final class PlanRules {

    public static final List<String> LIMIT_KEYS = List.of("maxUsers", "maxBranches", "maxFelPerMonth", "maxAiMessagesPerMonth");

    private PlanRules() {}

    /** Solo se activa lo que el plan no trae y solo se quita lo que sí trae; base nunca se quita. Ordenado. */
    public static List<List<String>> normalizeOverrides(Collection<String> planModules,
                                                        @Nullable Collection<String> enabled,
                                                        @Nullable Collection<String> disabled) {
        Set<String> enPlan = new LinkedHashSet<>(planModules);
        List<String> en = (enabled == null ? List.<String>of() : enabled).stream().distinct()
                .filter(m -> !ModuleCatalog.BASE.equals(m) && !enPlan.contains(m)).sorted().toList();
        List<String> dis = (disabled == null ? List.<String>of() : disabled).stream().distinct()
                .filter(m -> !ModuleCatalog.BASE.equals(m) && enPlan.contains(m)).sorted().toList();
        return List.of(en, dis);
    }

    /** En el orden del catálogo. */
    public static List<String> effectiveModules(Collection<String> planModules,
                                                @Nullable Collection<String> enabled,
                                                @Nullable Collection<String> disabled) {
        List<List<String>> o = normalizeOverrides(planModules, enabled, disabled);
        Set<String> set = new LinkedHashSet<>();
        set.add(ModuleCatalog.BASE);
        set.addAll(planModules);
        set.addAll(o.get(0));
        o.get(1).forEach(set::remove);
        List<String> out = new ArrayList<>();
        ModuleCatalog.CODES.forEach(c -> { if (set.contains(c)) out.add(c); });
        return out;
    }

    public static Map<String, Integer> effectiveLimits(Map<String, Integer> plan, Map<String, Integer> own) {
        Map<String, Integer> out = new LinkedHashMap<>();
        LIMIT_KEYS.forEach(k -> out.put(k, own.get(k) != null ? own.get(k) : plan.get(k)));
        return out;
    }

    /** Módulos desconocidos (para el 400). */
    public static List<String> unknownModules(@Nullable Collection<String> mods) {
        return mods == null ? List.of() : mods.stream().filter(m -> !ModuleCatalog.CODES.contains(m)).distinct().toList();
    }

    /** Límite válido: null (ilimitado) o entero ≥ 0. */
    public static boolean validLimit(@Nullable Object v) {
        if (v == null) return true;
        if (v instanceof Integer i) return i >= 0;
        if (v instanceof Long l) return l >= 0 && l <= Integer.MAX_VALUE;
        if (v instanceof BigDecimal b) return b.signum() >= 0 && b.stripTrailingZeros().scale() <= 0 && b.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        if (v instanceof Number n) return n.doubleValue() >= 0 && n.doubleValue() == Math.floor(n.doubleValue());
        return false;
    }

    /** Precio en quetzales: ≥ 0 y hasta 2 decimales. */
    public static boolean validPrice(@Nullable BigDecimal p) {
        return p != null && p.signum() >= 0 && p.stripTrailingZeros().scale() <= 2;
    }

    /** Uso frente a límite: ok | near (≥ 80 %) | over. Sin límite, ok. */
    public static String usageLevel(long used, @Nullable Integer limit) {
        if (limit == null) return "ok";
        if (used > limit) return "over";
        return used >= limit * 0.8 ? "near" : "ok";
    }
}
