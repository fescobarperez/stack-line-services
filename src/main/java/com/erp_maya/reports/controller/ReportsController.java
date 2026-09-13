package com.erp_maya.reports.controller;

import com.erp_maya.reports.dto.ReportsDtos.ProjectProfitability;
import com.erp_maya.reports.dto.ReportsDtos.SalesReport;
import com.erp_maya.reports.service.ProjectProfitabilityService;
import com.erp_maya.reports.service.ReportsService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

/** Reportería de la empresa. */
@Controller("/api/reports")
public class ReportsController {

    private final ReportsService service;
    private final ProjectProfitabilityService projectProfitability;

    public ReportsController(ReportsService service, ProjectProfitabilityService projectProfitability) {
        this.service = service;
        this.projectProfitability = projectProfitability;
    }

    /**
     * Top K de proyectos por rentabilidad, en las dos direcciones.
     *
     * `orderBy`: 'amount' (quetzales, default) o 'percent' (margen %).
     * `status`: estados separados por coma; 'all' los toma todos. Por defecto
     * solo 'closed': un proyecto abierto tiene el margen a medio cocinar y
     * compararlo con uno liquidado no dice nada.
     */
    @Get("/project-profitability")
    public ProjectProfitability projectProfitability(@Nullable @QueryValue Integer limit,
                                                     @Nullable @QueryValue String orderBy,
                                                     @Nullable @QueryValue String status) {
        return projectProfitability.ranking(limit, orderBy, status);
    }

    @Get("/sales")
    public SalesReport sales(@Nullable @QueryValue Integer days) {
        int window = days != null && days > 0 && days <= 365 ? days : 30;
        return service.salesReport(window);
    }
}
