package com.erp_maya.company.controller;

import com.erp_maya.company.dto.BranchDtos;
import com.erp_maya.company.service.BranchService;
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

@Controller("/api/branches")
public class BranchController {

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @Get
    public List<BranchDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public BranchDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public BranchDtos.Response create(@Valid @Body BranchDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public BranchDtos.Response update(Long id, @Valid @Body BranchDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
