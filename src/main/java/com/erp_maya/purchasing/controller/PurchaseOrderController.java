package com.erp_maya.purchasing.controller;

import com.erp_maya.purchasing.dto.PurchaseOrderDtos;
import com.erp_maya.purchasing.service.PurchaseOrderService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @Get
    public Page<PurchaseOrderDtos.Response> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Get("/{id}")
    public PurchaseOrderDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public PurchaseOrderDtos.Response create(@Valid @Body PurchaseOrderDtos.Request request) {
        return service.create(request);
    }

    @Post("/{id}/receive")
    public PurchaseOrderDtos.Response receive(Long id, @Nullable @Body PurchaseOrderDtos.ReceiveRequest request) {
        return service.receive(id, request);
    }

    @Post("/{id}/cancel")
    public PurchaseOrderDtos.Response cancel(Long id) {
        return service.cancel(id);
    }
}
