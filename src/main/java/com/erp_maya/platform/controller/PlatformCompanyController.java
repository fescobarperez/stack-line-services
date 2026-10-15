package com.erp_maya.platform.controller;

import com.erp_maya.platform.domain.PlatformOperator;
import com.erp_maya.platform.dto.PlatformCompanyDtos.*;
import com.erp_maya.platform.security.PlatformAccess;
import com.erp_maya.platform.service.InvitationService;
import com.erp_maya.platform.service.PlatformCompanyService;
import com.erp_maya.platform.service.PlatformCompanyService.Actor;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

/**
 * Empresas en la consola. Leer: cualquier operador. Crear, editar e invitar:
 * owner. Cambiar estado: owner y support. El PlatformAudienceFilter ya
 * garantiza que el token es de un operador.
 */
@Controller("/api/platform/companies")
@Secured(SecurityRule.IS_AUTHENTICATED)
@ExecuteOn(TaskExecutors.BLOCKING)
public class PlatformCompanyController {

    private final PlatformCompanyService service;

    public PlatformCompanyController(PlatformCompanyService service) {
        this.service = service;
    }

    private static Actor actor(Authentication auth) {
        Object name = auth.getAttributes().get("name");
        return new Actor(PlatformAccess.operatorId(auth), name == null ? "Operador" : name.toString());
    }

    @Get
    public ListResponse list(@Nullable @QueryValue String q, @Nullable @QueryValue String status,
                             @Nullable @QueryValue String plan) {
        return service.list(q, status, plan);
    }

    @Get("/check-code")
    public CheckCode checkCode(@Nullable @QueryValue String code) {
        return service.checkCode(code);
    }

    @Get("/{id}")
    public Detail get(@PathVariable Long id) {
        return service.get(id);
    }

    @Post
    public HttpResponse<Detail> create(@Body CreateRequest req, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return HttpResponse.created(service.create(req, actor(auth)));
    }

    @Patch("/{id}")
    public Detail update(@PathVariable Long id, @Body UpdateRequest req, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return service.update(id, req);
    }

    @Post("/{id}/status")
    public Detail changeStatus(@PathVariable Long id, @Body StatusRequest req, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER, PlatformOperator.SUPPORT);
        return service.changeStatus(id, req, actor(auth));
    }

    @Post("/{id}/admins/{userId}/invitation")
    public InvitationService.Invitation renewInvitation(@PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return service.renewInvitation(id, userId, actor(auth));
    }
}
