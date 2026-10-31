package com.erp_maya.lead.controller;

import com.erp_maya.lead.dto.LeadDtos;
import com.erp_maya.lead.service.LeadService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;

/** Prospectos (CRM). TINO los registra con POST; el equipo los consulta y cambia de estado. */
@Controller("/api/leads")
public class LeadController {

    private final LeadService leads;

    public LeadController(LeadService leads) {
        this.leads = leads;
    }

    @Post
    public HttpResponse<LeadDtos.Response> register(@Valid @Body LeadDtos.Create request) {
        return HttpResponse.ok(leads.register(request));
    }

    @Get
    public Page<LeadDtos.Response> list(@Nullable @QueryValue String status, Pageable pageable) {
        return leads.list(status, pageable);
    }

    @Patch("/{id}/status")
    public LeadDtos.Response status(Long id, @Valid @Body LeadDtos.StatusUpdate request) {
        return leads.updateStatus(id, request.status());
    }
}
