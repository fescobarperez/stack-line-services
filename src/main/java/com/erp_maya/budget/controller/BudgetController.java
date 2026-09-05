package com.erp_maya.budget.controller;

import com.erp_maya.budget.dto.BudgetDtos;
import com.erp_maya.budget.service.BudgetService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/budgets")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @Get
    public List<BudgetDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public BudgetDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public BudgetDtos.Response create(@Valid @Body BudgetDtos.Request request) {
        return service.create(request);
    }

    @Post("/{id}/lines")
    @Status(HttpStatus.CREATED)
    public BudgetDtos.LineResponse addLine(Long id, @Valid @Body BudgetDtos.LineRequest request) {
        return service.addLine(id, request);
    }
}
