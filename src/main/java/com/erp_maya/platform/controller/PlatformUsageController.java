package com.erp_maya.platform.controller;

import com.erp_maya.platform.domain.PlatformOperator;
import com.erp_maya.platform.security.PlatformAccess;
import com.erp_maya.platform.service.PlatformUsageService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import java.util.Map;

/**
 * Uso y costos (API.md · «Uso y costos»). Cualquier operador lo lee; el dinero
 * (precio, ingreso, costo, margen) solo lo ve owner.
 */
@Controller("/api/platform")
@Secured(SecurityRule.IS_AUTHENTICATED)
@ExecuteOn(TaskExecutors.BLOCKING)
public class PlatformUsageController {

    private final PlatformUsageService service;

    public PlatformUsageController(PlatformUsageService service) {
        this.service = service;
    }

    private static boolean owner(Authentication auth) {
        return PlatformOperator.OWNER.equals(PlatformAccess.role(auth));
    }

    @Get("/usage")
    public Map<String, Object> overview(@Nullable @QueryValue String month, Authentication auth) {
        return service.overview(month, owner(auth));
    }

    @Get("/companies/{id}/usage")
    public Map<String, Object> company(@PathVariable Long id, @Nullable @QueryValue String month, Authentication auth) {
        return service.company(id, month, owner(auth));
    }
}
