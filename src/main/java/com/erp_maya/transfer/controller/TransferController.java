package com.erp_maya.transfer.controller;

import com.erp_maya.transfer.dto.TransferDtos;
import com.erp_maya.transfer.service.TransferService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/transfers")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @Get
    public Page<TransferDtos.Response> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Get("/{id}")
    public TransferDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public TransferDtos.Response create(@Valid @Body TransferDtos.Request request) {
        return service.create(request);
    }

    @Post("/{id}/dispatch")
    public TransferDtos.Response dispatch(Long id) {
        return service.dispatch(id);
    }

    @Post("/{id}/receive")
    public TransferDtos.Response receive(Long id) {
        return service.receive(id);
    }
}
