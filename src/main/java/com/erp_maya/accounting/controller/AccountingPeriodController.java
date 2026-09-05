package com.erp_maya.accounting.controller;

import com.erp_maya.accounting.dto.AccountingPeriodDtos;
import com.erp_maya.accounting.service.AccountingPeriodService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/accounting-periods")
public class AccountingPeriodController {

    private final AccountingPeriodService service;

    public AccountingPeriodController(AccountingPeriodService service) {
        this.service = service;
    }

    @Get
    public List<AccountingPeriodDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public AccountingPeriodDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public AccountingPeriodDtos.Response create(@Valid @Body AccountingPeriodDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}/close")
    public AccountingPeriodDtos.Response close(Long id) {
        return service.close(id);
    }
}
