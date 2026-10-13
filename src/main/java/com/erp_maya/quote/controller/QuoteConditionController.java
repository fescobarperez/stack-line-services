package com.erp_maya.quote.controller;

import com.erp_maya.quote.dto.QuoteConditionDtos;
import com.erp_maya.quote.service.QuoteConditionService;
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

/** Mantenimiento de las condiciones comerciales que salen en cada cotización. */
@Controller("/api/quote-conditions")
public class QuoteConditionController {

    private final QuoteConditionService service;

    public QuoteConditionController(QuoteConditionService service) {
        this.service = service;
    }

    @Get
    public List<QuoteConditionDtos.Response> list() {
        return service.list();
    }

    @Post
    @Status(HttpStatus.CREATED)
    public QuoteConditionDtos.Response create(@Valid @Body QuoteConditionDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public QuoteConditionDtos.Response update(Long id, @Valid @Body QuoteConditionDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
