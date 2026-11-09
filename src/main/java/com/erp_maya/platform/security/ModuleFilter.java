package com.erp_maya.platform.security;

import com.erp_maya.platform.service.ModuleCatalog;
import com.erp_maya.platform.service.SubscriptionService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Una empresa solo usa los módulos de su plan (más sus excepciones). La app
 * ya oculta el menú; esto es lo que lo hace cumplir: 403 MODULE_NOT_ENABLED.
 * Aplica a usuarios y al token de servicio del agente (los dos llevan companyId).
 */
@ServerFilter("/api/**")
public class ModuleFilter {

    @Serdeable
    public record ModuleError(int status, String error, String message, String code, String module) {}

    private final SubscriptionService subscriptions;

    public ModuleFilter(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @RequestFilter
    @Order(Ordered.LOWEST_PRECEDENCE - 3)
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Nullable
    public HttpResponse<ModuleError> filterRequest(HttpRequest<?> request) {
        String module = ModuleCatalog.moduleFor(request.getPath());
        if (module == null) {
            return null;
        }
        Object companyId = request.getUserPrincipal(Authentication.class)
                .map(a -> a.getAttributes().get("companyId")).orElse(null);
        if (companyId == null) {
            return null;
        }
        long id;
        try {
            id = Long.parseLong(companyId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
        if (subscriptions.modules(id).contains(module)) {
            return null;
        }
        return HttpResponse.<ModuleError>status(HttpStatus.FORBIDDEN).body(new ModuleError(403, "Forbidden",
                "Tu plan no incluye este módulo. Para activarlo, contacta a Stackline.", "MODULE_NOT_ENABLED", module));
    }
}
