package com.erp_maya.company.controller;

import com.erp_maya.company.dto.EstablishmentDtos;
import com.erp_maya.company.service.EstablishmentService;
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

@Controller("/api/establishments")
public class EstablishmentController {

    private final EstablishmentService service;

    public EstablishmentController(EstablishmentService service) {
        this.service = service;
    }

    @Get
    public List<EstablishmentDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public EstablishmentDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public EstablishmentDtos.Response create(@Valid @Body EstablishmentDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public EstablishmentDtos.Response update(Long id, @Valid @Body EstablishmentDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
