package com.erp_maya.marketing.controller;

import com.erp_maya.marketing.dto.PromotionDtos;
import com.erp_maya.marketing.service.PromotionService;
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

@Controller("/api/promotions")
public class PromotionController {

    private final PromotionService service;

    public PromotionController(PromotionService service) {
        this.service = service;
    }

    @Get
    public List<PromotionDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public PromotionDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public PromotionDtos.Response create(@Valid @Body PromotionDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public PromotionDtos.Response update(Long id, @Valid @Body PromotionDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }

    @Post("/{id}/usage")
    public PromotionDtos.Response registerUsage(Long id, @Body PromotionDtos.UsageRequest request) {
        return service.registerUsage(id, request);
    }
}
