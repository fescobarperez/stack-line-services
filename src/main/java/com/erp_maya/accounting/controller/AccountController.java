package com.erp_maya.accounting.controller;

import com.erp_maya.accounting.dto.AccountDtos;
import com.erp_maya.accounting.service.AccountService;
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

@Controller("/api/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @Get
    public List<AccountDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public AccountDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public AccountDtos.Response create(@Valid @Body AccountDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public AccountDtos.Response update(Long id, @Valid @Body AccountDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
