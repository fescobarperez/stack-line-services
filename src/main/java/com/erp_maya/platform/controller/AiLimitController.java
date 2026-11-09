package com.erp_maya.platform.controller;

import com.erp_maya.common.TenantContext;
import com.erp_maya.platform.service.SubscriptionService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Cuántos mensajes de IA permite al mes el plan de la empresa actual. Lo lee
 * agents-services con el token de servicio de la empresa para cortar al
 * agente cuando se agotan (el conteo lo lleva agents-services).
 */
@Controller("/api/company/ai-limit")
@ExecuteOn(TaskExecutors.BLOCKING)
public class AiLimitController {

    /** {@code monthlyMessages} nulo = sin límite. */
    @Serdeable
    public record AiLimit(boolean enabled, @Nullable Integer monthlyMessages) {}

    private final SubscriptionService subscriptions;
    private final TenantContext tenant;

    public AiLimitController(SubscriptionService subscriptions, TenantContext tenant) {
        this.subscriptions = subscriptions;
        this.tenant = tenant;
    }

    @Get
    public AiLimit get() {
        SubscriptionService.Effective e = subscriptions.effective(tenant.getCompanyId());
        return new AiLimit(e.modules().contains("ai"), e.limits().get("maxAiMessagesPerMonth"));
    }
}
