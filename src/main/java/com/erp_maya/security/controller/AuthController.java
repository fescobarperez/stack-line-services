package com.erp_maya.security.controller;

import com.erp_maya.security.dto.AuthDtos;
import com.erp_maya.security.service.AuthService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.validation.Valid;

/** Autenticación: login público que devuelve el JWT de sesión. */
@Controller("/api/auth")
@Secured(SecurityRule.IS_ANONYMOUS)
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @Post("/login")
    public AuthDtos.LoginResponse login(@Valid @Body AuthDtos.LoginRequest request) {
        return service.login(request);
    }
}
