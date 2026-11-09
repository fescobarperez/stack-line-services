package com.erp_maya.partner.controller;

import com.erp_maya.partner.dto.SupplierWebsiteDtos;
import com.erp_maya.partner.service.SupplierWebsiteService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/supplier-websites")
public class SupplierWebsiteController {

    private final SupplierWebsiteService service;

    public SupplierWebsiteController(SupplierWebsiteService service) {
        this.service = service;
    }

    @Get
    public List<SupplierWebsiteDtos.Response> list(@Nullable @QueryValue Long supplierId) {
        return service.list(supplierId);
    }

    @Get("/{id}")
    public SupplierWebsiteDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public SupplierWebsiteDtos.Response create(@Valid @Body SupplierWebsiteDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public SupplierWebsiteDtos.Response update(Long id, @Valid @Body SupplierWebsiteDtos.Request request) {
        return service.update(id, request);
    }
}
