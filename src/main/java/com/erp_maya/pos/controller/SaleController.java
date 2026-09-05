package com.erp_maya.pos.controller;

import com.erp_maya.pos.dto.SaleDtos;
import com.erp_maya.pos.service.SaleService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/sales")
public class SaleController {

    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    @Get
    public Page<SaleDtos.Response> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Get("/{id}")
    public SaleDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public SaleDtos.Response create(@Valid @Body SaleDtos.Request request) {
        return service.create(request);
    }
}
