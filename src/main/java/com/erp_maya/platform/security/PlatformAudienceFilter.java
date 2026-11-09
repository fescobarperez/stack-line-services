package com.erp_maya.platform.security;

import com.erp_maya.platform.dto.PlatformDtos.PlatformError;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.security.authentication.Authentication;

/**
 * Separa los dos mundos que comparten el mismo secreto JWT: el token de un
 * operador no abre el ERP de ninguna empresa, y el de un usuario de empresa
 * no abre la consola. Corre después de la seguridad (el principal ya existe),
 * igual que el TenantFilter.
 */
@ServerFilter("/api/**")
public class PlatformAudienceFilter {

    @RequestFilter
    @Order(Ordered.LOWEST_PRECEDENCE - 1)
    @Nullable
    public HttpResponse<PlatformError> filterRequest(HttpRequest<?> request) {
        String kind = PlatformAccess.kind(request.getUserPrincipal(Authentication.class).orElse(null));
        if (PlatformAccess.decide(request.getPath(), kind) == PlatformAccess.Decision.ALLOW) {
            return null;
        }
        return HttpResponse.<PlatformError>status(HttpStatus.FORBIDDEN)
                .body(PlatformError.of("WRONG_AUDIENCE", "Este token no sirve para esta parte de la API"));
    }
}
