package com.erp_maya.returns.controller;

import com.erp_maya.returns.dto.CreditNoteDtos;
import com.erp_maya.returns.service.CreditNoteService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/credit-notes")
public class CreditNoteController {

    private final CreditNoteService service;

    public CreditNoteController(CreditNoteService service) {
        this.service = service;
    }

    @Get
    public Page<CreditNoteDtos.Response> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Get("/{id}")
    public CreditNoteDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public CreditNoteDtos.Response create(@Valid @Body CreditNoteDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}/fel")
    public CreditNoteDtos.Response retryFel(Long id, @Nullable @Body CreditNoteDtos.FelRequest request) {
        return service.retryFel(id, request);
    }
}
