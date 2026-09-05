package com.erp_maya.payable.controller;

import com.erp_maya.payable.dto.PayableDtos;
import com.erp_maya.payable.service.PayableService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/purchase-invoices")
public class PurchaseInvoiceController {

    private final PayableService service;

    public PurchaseInvoiceController(PayableService service) {
        this.service = service;
    }

    @Get
    public Page<PayableDtos.InvoiceResponse> list(Pageable pageable) {
        return service.listInvoices(pageable);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public PayableDtos.InvoiceResponse create(@Valid @Body PayableDtos.InvoiceRequest request) {
        return service.createInvoice(request);
    }
}
