package com.erp_maya.receivable.controller;

import com.erp_maya.receivable.dto.PaymentDtos;
import com.erp_maya.receivable.service.PaymentService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @Get
    public Page<PaymentDtos.Response> list(@Nullable @QueryValue Long clientId, Pageable pageable) {
        return service.list(clientId, pageable);
    }

    @Get("/{id}")
    public PaymentDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public PaymentDtos.Response create(@Valid @Body PaymentDtos.Request request) {
        return service.create(request);
    }
}
