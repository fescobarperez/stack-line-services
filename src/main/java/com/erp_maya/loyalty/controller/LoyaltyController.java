package com.erp_maya.loyalty.controller;

import com.erp_maya.loyalty.dto.LoyaltyDtos;
import com.erp_maya.loyalty.service.LoyaltyService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/loyalty")
public class LoyaltyController {

    private final LoyaltyService service;

    public LoyaltyController(LoyaltyService service) {
        this.service = service;
    }

    @Get("/accounts")
    public List<LoyaltyDtos.AccountResponse> list() {
        return service.list();
    }

    @Get("/accounts/{id}")
    public LoyaltyDtos.AccountResponse get(Long id) {
        return service.get(id);
    }

    @Post("/accounts")
    @Status(HttpStatus.CREATED)
    public LoyaltyDtos.AccountResponse create(@Valid @Body LoyaltyDtos.AccountRequest request) {
        return service.create(request);
    }

    @Get("/accounts/{id}/movements")
    public List<LoyaltyDtos.MovementResponse> movements(Long id) {
        return service.movements(id);
    }

    @Post("/accounts/{id}/movements")
    @Status(HttpStatus.CREATED)
    public LoyaltyDtos.MovementResponse addMovement(Long id, @Valid @Body LoyaltyDtos.MovementRequest request) {
        return service.addMovement(id, request);
    }
}
