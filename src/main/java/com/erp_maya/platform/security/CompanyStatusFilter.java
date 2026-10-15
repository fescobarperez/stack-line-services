package com.erp_maya.platform.security;

import com.erp_maya.common.ApiError;
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

/**
 * Una empresa suspendida o cancelada deja de operar también con las sesiones
 * que ya estaban abiertas: su token sigue firmado, pero aquí se corta con 401
 * y la app vuelve al login (que ya la rechaza).
 */
@ServerFilter("/api/**")
public class CompanyStatusFilter {

    private final CompanyStatusCache cache;

    public CompanyStatusFilter(CompanyStatusCache cache) {
        this.cache = cache;
    }

    @RequestFilter
    @Order(Ordered.LOWEST_PRECEDENCE - 2)
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Nullable
    public HttpResponse<ApiError> filterRequest(HttpRequest<?> request) {
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
        if (cache.operates(id)) {
            return null;
        }
        return HttpResponse.<ApiError>status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(401, "Unauthorized", "La empresa está suspendida. Contacta al administrador."));
    }
}
