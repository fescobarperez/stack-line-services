package com.erp_maya.dashboard.controller;

import com.erp_maya.dashboard.dto.DashboardDtos.Dashboard;
import com.erp_maya.dashboard.service.DashboardService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

/** Dashboard general: métricas y series agregadas de la empresa. */
@Controller("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @Get
    public Dashboard dashboard(@Nullable @QueryValue Integer days) {
        int window = days != null && days > 0 && days <= 90 ? days : 14;
        return service.build(window);
    }
}
