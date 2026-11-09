package com.erp_maya.platform.controller;

import com.erp_maya.common.InvalidCredentialsException;
import com.erp_maya.platform.dto.PlatformDtos;
import com.erp_maya.platform.dto.PlatformDtos.PlatformError;
import com.erp_maya.platform.service.PlatformAuthService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import jakarta.validation.Valid;

/** Sesión de los operadores de la consola de plataforma. */
@Controller("/api/platform/auth")
@ExecuteOn(TaskExecutors.BLOCKING)
public class PlatformAuthController {

    private final PlatformAuthService service;

    public PlatformAuthController(PlatformAuthService service) {
        this.service = service;
    }

    @Post("/login")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<?> login(@Valid @Body PlatformDtos.LoginRequest request) {
        try {
            return HttpResponse.ok(service.login(request));
        } catch (InvalidCredentialsException e) {
            return HttpResponse.<PlatformError>status(HttpStatus.UNAUTHORIZED)
                    .body(PlatformError.of("BAD_CREDENTIALS", e.getMessage()));
        }
    }

    @Get("/me")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<?> me(Authentication auth) {
        try {
            return HttpResponse.ok(service.me(auth));
        } catch (InvalidCredentialsException e) {
            return HttpResponse.<PlatformError>status(HttpStatus.UNAUTHORIZED)
                    .body(PlatformError.of("UNAUTHORIZED", e.getMessage()));
        }
    }
}
