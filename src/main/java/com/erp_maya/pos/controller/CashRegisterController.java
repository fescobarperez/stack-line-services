package com.erp_maya.pos.controller;

import com.erp_maya.pos.dto.CashRegisterDtos;
import com.erp_maya.pos.service.CashRegisterService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/cash-registers")
public class CashRegisterController {

    private final CashRegisterService service;

    public CashRegisterController(CashRegisterService service) {
        this.service = service;
    }

    @Get
    public List<CashRegisterDtos.Response> list(@Nullable @QueryValue String status) {
        return service.list(status);
    }

    @Get("/{id}")
    public CashRegisterDtos.Response get(Long id) {
        return service.get(id);
    }

    /** Turnos abiertos de días anteriores: bloquean la apertura hasta que se cierren. */
    @Get("/pending")
    public List<CashRegisterDtos.Response> pending() {
        return service.pending();
    }

    @Post("/open")
    @Status(HttpStatus.CREATED)
    public CashRegisterDtos.Response open(@Valid @Body CashRegisterDtos.OpenRequest request) {
        return service.open(request);
    }

    @Put("/{id}/close")
    public CashRegisterDtos.Response close(Long id, @Valid @Body CashRegisterDtos.CloseRequest request) {
        return service.close(id, request);
    }
}
