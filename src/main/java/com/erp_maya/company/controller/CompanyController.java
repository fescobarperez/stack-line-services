package com.erp_maya.company.controller;

import com.erp_maya.company.dto.CompanyDtos;
import com.erp_maya.company.service.CompanyService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

/** Empresa del inquilino actual (su propio registro). */
@Controller("/api/company")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @Get
    public CompanyDtos.Response current() {
        return service.getCurrent();
    }

    @Put
    public CompanyDtos.Response update(@Valid @Body CompanyDtos.Request request) {
        return service.updateCurrent(request);
    }
}
