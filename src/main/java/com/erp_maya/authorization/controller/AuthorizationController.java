package com.erp_maya.authorization.controller;

import com.erp_maya.authorization.dto.AuthorizationDtos;
import com.erp_maya.authorization.service.AuthorizationService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Los tres verbos del motor. Cualquier módulo (POS, notas de crédito, pagos)
 * habla solo con esto; no hay endpoints por dominio.
 */
@Controller("/api/authorizations")
public class AuthorizationController {

    private final AuthorizationService service;

    public AuthorizationController(AuthorizationService service) {
        this.service = service;
    }

    /** ¿Requiere autorización? El consumidor pregunta siempre, nunca decide. */
    @Post("/evaluate")
    public AuthorizationDtos.EvaluateResponse evaluate(@Valid @Body AuthorizationDtos.EvaluateRequest request) {
        return service.evaluate(request);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public AuthorizationDtos.Response create(@Valid @Body AuthorizationDtos.CreateRequest request,
                                             Authentication authentication) {
        return service.create(request, userId(authentication));
    }

    @Post("/{id}/resolve")
    public AuthorizationDtos.Response resolve(Long id, @Valid @Body AuthorizationDtos.ResolveRequest request,
                                              Authentication authentication) {
        return service.resolve(id, request, userId(authentication));
    }

    @Get("/pending")
    public List<AuthorizationDtos.Response> pending() {
        return service.pending();
    }

    @Get("/{id}")
    public AuthorizationDtos.Response get(Long id) {
        return service.get(id);
    }

    /** El actor sale del token, no del body. */
    private static Long userId(Authentication auth) {
        Object v = auth == null ? null : auth.getAttributes().get("userId");
        if (v instanceof Number n) return n.longValue();
        throw new IllegalStateException("El token no identifica al usuario; vuelve a iniciar sesión");
    }
}
