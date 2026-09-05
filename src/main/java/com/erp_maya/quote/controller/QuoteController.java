package com.erp_maya.quote.controller;

import com.erp_maya.quote.dto.QuoteDtos;
import com.erp_maya.quote.service.QuoteService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/quotes")
public class QuoteController {

    private final QuoteService service;

    public QuoteController(QuoteService service) {
        this.service = service;
    }

    @Get
    public Page<QuoteDtos.Response> list(@Nullable @QueryValue String partyType, Pageable pageable) {
        return service.list(partyType, pageable);
    }

    @Get("/{id}")
    public QuoteDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public QuoteDtos.Response create(@Valid @Body QuoteDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}/status")
    public QuoteDtos.Response updateStatus(Long id, @Valid @Body QuoteDtos.StatusRequest request) {
        return service.updateStatus(id, request);
    }
}
