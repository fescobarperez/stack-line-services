package com.erp_maya.common;

import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.security.authentication.Authentication;

/**
 * Fija el inquilino (tenant) de cada request a /api/** en el {@link TenantContext}
 * a partir del claim {@code companyId} del JWT autenticado. Se ejecuta después del
 * filtro de seguridad (LOWEST_PRECEDENCE = más tarde) para que el principal ya exista.
 * Los endpoints anónimos (login) no traen principal → el contexto queda vacío.
 */
@ServerFilter("/api/**")
public class TenantFilter {

    private final TenantContext tenant;

    public TenantFilter(TenantContext tenant) {
        this.tenant = tenant;
    }

    @RequestFilter
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void filterRequest(HttpRequest<?> request) {
        request.getUserPrincipal(Authentication.class).ifPresent(auth -> {
            Object companyId = auth.getAttributes().get("companyId");
            if (companyId != null) {
                try {
                    tenant.setCompanyId(Long.valueOf(companyId.toString()));
                } catch (NumberFormatException ignored) {
                    // claim inválido → el contexto queda vacío y falla como tenant ausente
                }
            }
        });
    }
}
