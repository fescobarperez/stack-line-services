package com.erp_maya.nav.controller;

import com.erp_maya.nav.dto.NavCounters;
import com.erp_maya.nav.service.NavCounterService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

/** Contadores de pendientes para el menú lateral de maya-ui. */
@Controller("/api/nav")
public class NavCounterController {

    private final NavCounterService service;

    public NavCounterController(NavCounterService service) {
        this.service = service;
    }

    @Get("/counters")
    public NavCounters counters() {
        return service.counters();
    }
}
