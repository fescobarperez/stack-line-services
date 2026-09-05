package com.erp_maya.pos.controller;

import com.erp_maya.pos.dto.CashPointDtos;
import com.erp_maya.pos.service.CashPointService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/cash-points")
public class CashPointController {

    private final CashPointService service;

    public CashPointController(CashPointService service) {
        this.service = service;
    }

    @Get
    public List<CashPointDtos.Response> list(@Nullable @QueryValue Long branchId) {
        return service.list(branchId);
    }

    @Get("/{id}")
    public CashPointDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public CashPointDtos.Response create(@Valid @Body CashPointDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public CashPointDtos.Response update(Long id, @Valid @Body CashPointDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
