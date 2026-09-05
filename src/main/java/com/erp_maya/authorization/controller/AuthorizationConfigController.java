package com.erp_maya.authorization.controller;

import com.erp_maya.authorization.domain.AuthorizationLevel;
import com.erp_maya.authorization.domain.AuthorizationRule;
import com.erp_maya.authorization.domain.AuthorizationType;
import com.erp_maya.authorization.repository.AuthorizationRepositories.*;
import com.erp_maya.common.TenantContext;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

/** Solo lectura del catálogo: niveles, tipos y reglas. La edición vendrá después. */
@Controller("/api/authorization-config")
public class AuthorizationConfigController {

    private final Levels levels;
    private final Types types;
    private final Rules rules;
    private final TenantContext tenant;

    public AuthorizationConfigController(Levels levels, Types types, Rules rules, TenantContext tenant) {
        this.levels = levels; this.types = types; this.rules = rules; this.tenant = tenant;
    }

    @Get("/levels")
    public List<AuthorizationLevel> levels() {
        return levels.findByCompanyIdOrderByRankAsc(tenant.getCompanyId());
    }

    @Get("/types")
    public List<AuthorizationType> types() {
        return types.findByCompanyIdOrderByNameAsc(tenant.getCompanyId());
    }

    @Get("/rules")
    public List<AuthorizationRule> rules() {
        Long companyId = tenant.getCompanyId();
        return types.findByCompanyIdOrderByNameAsc(companyId).stream()
                .flatMap(t -> rules.findByCompanyIdAndTypeIdAndActiveTrue(companyId, t.getId()).stream())
                .toList();
    }
}
