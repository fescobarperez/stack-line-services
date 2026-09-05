package com.erp_maya.reports.controller;

import com.erp_maya.reports.dto.ReportsDtos.SalesReport;
import com.erp_maya.reports.service.ReportsService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

/** Reportería de la empresa. */
@Controller("/api/reports")
public class ReportsController {

    private final ReportsService service;

    public ReportsController(ReportsService service) {
        this.service = service;
    }

    @Get("/sales")
    public SalesReport sales(@Nullable @QueryValue Integer days) {
        int window = days != null && days > 0 && days <= 365 ? days : 30;
        return service.salesReport(window);
    }
}
