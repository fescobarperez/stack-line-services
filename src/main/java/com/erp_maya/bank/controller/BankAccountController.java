package com.erp_maya.bank.controller;

import com.erp_maya.bank.dto.BankDtos;
import com.erp_maya.bank.service.BankService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/bank-accounts")
public class BankAccountController {

    private final BankService service;

    public BankAccountController(BankService service) {
        this.service = service;
    }

    @Get
    public List<BankDtos.AccountResponse> list() {
        return service.list();
    }

    @Get("/{id}")
    public BankDtos.AccountResponse get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public BankDtos.AccountResponse create(@Valid @Body BankDtos.AccountRequest request) {
        return service.create(request);
    }

    @Put("/{id}")
    public BankDtos.AccountResponse update(Long id, @Valid @Body BankDtos.AccountRequest request) {
        return service.update(id, request);
    }

    // ── Movimientos ───────────────────────────────────────────────────────
    @Get("/{id}/movements")
    public List<BankDtos.MovementResponse> listMovements(Long id) {
        return service.listMovements(id);
    }

    @Post("/{id}/movements")
    @Status(HttpStatus.CREATED)
    public BankDtos.MovementResponse addMovement(Long id, @Valid @Body BankDtos.MovementRequest request) {
        return service.addMovement(id, request);
    }

    // ── Conciliaciones ────────────────────────────────────────────────────
    @Get("/{id}/reconciliations")
    public List<BankDtos.ReconcileResponse> listReconciliations(Long id) {
        return service.listReconciliations(id);
    }

    @Post("/{id}/reconciliations")
    @Status(HttpStatus.CREATED)
    public BankDtos.ReconcileResponse reconcile(Long id, @Valid @Body BankDtos.ReconcileRequest request) {
        return service.reconcile(id, request);
    }
}
