package com.erp_maya.quote.controller;

import com.erp_maya.quote.dto.QuoteDtos;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import com.erp_maya.quote.dto.QuotePlanDtos;
import com.erp_maya.quote.service.QuoteService;
import com.erp_maya.quote.service.QuoteChargeService;
import com.erp_maya.quote.service.QuotePlanService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/quotes")
public class QuoteController {

    private final QuoteService service;
    private final QuoteChargeService charges;
    private final QuotePlanService plan;

    public QuoteController(QuoteService service, QuoteChargeService charges, QuotePlanService plan) {
        this.service = service;
        this.charges = charges;
        this.plan = plan;
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

    // ── Gastos / cargos de la cotización ──────────────────────────────────
    @Get("/{id}/charges")
    public QuoteChargeDtos.Summary charges(Long id) {
        return charges.getSummary(id);
    }

    @Post("/{id}/charges")
    @Status(HttpStatus.CREATED)
    public QuoteChargeDtos.Summary addCharge(Long id, @Valid @Body QuoteChargeDtos.Request request) {
        return charges.addCharge(id, request);
    }

    @Delete("/{id}/charges/{chargeId}")
    public QuoteChargeDtos.Summary deleteCharge(Long id, Long chargeId) {
        return charges.deleteCharge(id, chargeId);
    }

    // ── Plan de pagos y cobros de la cotización ───────────────────────────
    @Get("/{id}/plan")
    public QuotePlanDtos.Plan plan(Long id) {
        return plan.getPlan(id);
    }

    @Post("/{id}/plan/terms")
    @Status(HttpStatus.CREATED)
    public QuotePlanDtos.Plan addTerm(Long id, @Valid @Body QuotePlanDtos.TermRequest request) {
        return plan.addTerm(id, request);
    }

    @Delete("/{id}/plan/terms/{termId}")
    public QuotePlanDtos.Plan deleteTerm(Long id, Long termId) {
        return plan.deleteTerm(id, termId);
    }
}
