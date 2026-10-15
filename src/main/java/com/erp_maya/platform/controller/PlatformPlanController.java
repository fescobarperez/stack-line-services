package com.erp_maya.platform.controller;

import com.erp_maya.platform.domain.PlatformOperator;
import com.erp_maya.platform.dto.PlanDtos.*;
import com.erp_maya.platform.security.PlatformAccess;
import com.erp_maya.platform.service.ModuleCatalog;
import com.erp_maya.platform.service.PlatformPlanService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import java.util.List;

/**
 * Módulos, planes y plan de cada empresa. Leer: cualquier operador (el precio
 * solo lo ve owner). Escribir: owner.
 */
@Controller("/api/platform")
@Secured(SecurityRule.IS_AUTHENTICATED)
@ExecuteOn(TaskExecutors.BLOCKING)
public class PlatformPlanController {

    private final PlatformPlanService service;

    public PlatformPlanController(PlatformPlanService service) {
        this.service = service;
    }

    private static boolean owner(Authentication auth) {
        return PlatformOperator.OWNER.equals(PlatformAccess.role(auth));
    }

    @Get("/modules")
    public List<ModuleCatalog.Module> modules() {
        return ModuleCatalog.MODULES;
    }

    @Get("/plans")
    public List<PlanView> plans(Authentication auth) {
        return service.list(owner(auth));
    }

    @Post("/plans")
    public HttpResponse<PlanView> create(@Body PlanIn in, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return HttpResponse.created(service.create(in));
    }

    @Patch("/plans/{id}")
    public PlanView update(@PathVariable Long id, @Body PlanIn in, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return service.update(id, in);
    }

    @Get("/companies/{id}/subscription")
    public SubscriptionView subscription(@PathVariable Long id, Authentication auth) {
        return service.get(id, owner(auth));
    }

    @Put("/companies/{id}/subscription")
    public SubscriptionView saveSubscription(@PathVariable Long id, @Body SubscriptionIn in, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        Object name = auth.getAttributes().get("name");
        return service.save(id, in, name == null ? "Operador" : name.toString());
    }
}
