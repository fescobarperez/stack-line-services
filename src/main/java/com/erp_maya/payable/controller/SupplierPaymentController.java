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

@Controller("/api/supplier-payments")
public class SupplierPaymentController {

    private final PayableService service;

    public SupplierPaymentController(PayableService service) {
        this.service = service;
    }

    @Get
    public Page<PayableDtos.PaymentResponse> list(Pageable pageable) {
        return service.listPayments(pageable);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public PayableDtos.PaymentResponse create(@Valid @Body PayableDtos.PaymentRequest request) {
        return service.createPayment(request);
    }
}
