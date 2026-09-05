package com.erp_maya.costcenter.controller;

import com.erp_maya.costcenter.dto.CostCenterDtos;
import com.erp_maya.costcenter.service.CostCenterService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/cost-centers")
public class CostCenterController {

    private final CostCenterService service;

    public CostCenterController(CostCenterService service) {
        this.service = service;
    }

    @Get
    public List<CostCenterDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public CostCenterDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public CostCenterDtos.Response create(@Valid @Body CostCenterDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public CostCenterDtos.Response update(Long id, @Valid @Body CostCenterDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
