package com.erp_maya.receivable.controller;

import com.erp_maya.receivable.dto.AgingDtos.Aging;
import com.erp_maya.receivable.service.ReceivableAgingService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

/** Antigüedad de saldos por cobrar (CxC aging). */
@Controller("/api/receivables")
public class ReceivableAgingController {

    private final ReceivableAgingService service;

    public ReceivableAgingController(ReceivableAgingService service) {
        this.service = service;
    }

    @Get("/aging")
    public Aging aging() {
        return service.aging();
    }
}
