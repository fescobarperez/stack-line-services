package com.erp_maya.fel.controller;

import com.erp_maya.fel.dto.FelDtos;
import com.erp_maya.fel.service.FelService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/fel")
public class FelController {

    private final FelService service;

    public FelController(FelService service) {
        this.service = service;
    }

    @Get("/documents")
    public Page<FelDtos.Response> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Get("/documents/{id}")
    public FelDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post("/certify")
    @Status(HttpStatus.CREATED)
    public FelDtos.Response certify(@Valid @Body FelDtos.CertifyRequest request) {
        return service.certify(request);
    }
}
