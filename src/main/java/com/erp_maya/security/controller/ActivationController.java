package com.erp_maya.security.controller;

import com.erp_maya.platform.service.ActivationService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

/** Activación de cuenta desde el enlace de invitación (pantalla /activar de la app). */
@Controller("/api/auth/activation")
@Secured(SecurityRule.IS_ANONYMOUS)
@ExecuteOn(TaskExecutors.BLOCKING)
public class ActivationController {

    private final ActivationService service;

    public ActivationController(ActivationService service) {
        this.service = service;
    }

    @Get
    public ActivationService.ActivationInfo info(@Nullable @QueryValue("t") String token) {
        return service.info(token);
    }

    @Post
    public ActivationService.ActivationInfo activate(@Body ActivationService.ActivateRequest req) {
        return service.activate(req);
    }
}
