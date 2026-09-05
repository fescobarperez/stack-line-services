package com.erp_maya.payroll.controller;

import com.erp_maya.payroll.dto.PayrollDtos;
import com.erp_maya.payroll.service.PayrollService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/payroll-periods")
public class PayrollController {

    private final PayrollService service;

    public PayrollController(PayrollService service) {
        this.service = service;
    }

    @Get
    public List<PayrollDtos.PeriodResponse> list() {
        return service.list();
    }

    @Get("/{id}")
    public PayrollDtos.PeriodResponse get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public PayrollDtos.PeriodResponse create(@Valid @Body PayrollDtos.PeriodRequest request) {
        return service.create(request);
    }

    /** Crea (o reutiliza) el período por su código y lo procesa — idempotente. */
    @Post("/generate")
    public PayrollDtos.PeriodResponse generate(@Valid @Body PayrollDtos.PeriodRequest request) {
        return service.generate(request);
    }

    @Post("/{id}/process")
    public PayrollDtos.PeriodResponse process(Long id) {
        return service.process(id);
    }

    @Post("/{id}/close")
    public PayrollDtos.PeriodResponse close(Long id) {
        return service.close(id);
    }

    @Get("/{id}/igss")
    public PayrollDtos.IgssReport igssReport(Long id) {
        return service.igssReport(id);
    }

    @Get("/{id}/isr")
    public PayrollDtos.IsrReport isrReport(Long id) {
        return service.isrReport(id);
    }
}
