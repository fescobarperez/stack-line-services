package com.erp_maya.platform.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Planes, módulos y plan de una empresa (API.md · «Planes y módulos»). */
public final class PlanDtos {

    private PlanDtos() {}

    @Serdeable
    public record PlanView(Long id, String code, String name, @Nullable String description, @Nullable BigDecimal price,
                           List<String> modules, Map<String, Integer> limits, boolean active, long companiesCount) {}

    @Serdeable
    public record PlanIn(@Nullable String code, @Nullable String name, @Nullable String description,
                         @Nullable BigDecimal price, @Nullable List<String> modules,
                         @Nullable Map<String, Object> limits, @Nullable Boolean active) {}

    @Serdeable
    public record Overrides(List<String> enabled, List<String> disabled) {}

    @Serdeable
    public record Effective(@Nullable BigDecimal price, List<String> modules, Map<String, Integer> limits) {}

    @Serdeable
    public record Usage(long users, long branches, long felThisMonth, @Nullable Long aiMessagesThisMonth) {}

    @Serdeable
    public record SubscriptionView(PlatformCompanyDtos.PlanRef plan, Overrides overrides, Map<String, Integer> limitOverrides,
                                   @Nullable BigDecimal priceOverride, Effective effective, Usage usage) {}

    @Serdeable
    public record OverridesIn(@Nullable List<String> enabled, @Nullable List<String> disabled) {}

    @Serdeable
    public record SubscriptionIn(@Nullable Long planId, @Nullable OverridesIn overrides,
                                 @Nullable Map<String, Object> limitOverrides, @Nullable BigDecimal priceOverride) {}
}
