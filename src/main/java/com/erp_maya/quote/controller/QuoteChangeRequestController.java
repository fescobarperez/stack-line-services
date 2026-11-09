package com.erp_maya.quote.controller;

import com.erp_maya.quote.dto.ChangeRequestDtos;
import com.erp_maya.quote.service.QuoteChangeRequestService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.util.List;

/** Solicitudes de cambio del cliente y el «Aplicar» del vendedor. */
@Controller("/api/quotes/{quoteId}/change-requests")
public class QuoteChangeRequestController {

    private final QuoteChangeRequestService service;

    public QuoteChangeRequestController(QuoteChangeRequestService service) {
        this.service = service;
    }

    @Get
    public List<ChangeRequestDtos.Response> list(Long quoteId) {
        return service.list(quoteId);
    }

    /** Catálogo de motivos para rechazar o ajustar (el de la empresa o el de por defecto). */
    @Get("/reasons")
    public List<ChangeRequestDtos.Reason> reasons(Long quoteId) {
        return service.motivos();
    }

    /** La registra el asistente cuando el cliente pide un cambio y la cotización ya la tomó un vendedor. */
    @Post
    @Status(HttpStatus.CREATED)
    public ChangeRequestDtos.Response create(Long quoteId, @Valid @Body ChangeRequestDtos.CreateRequest request) {
        return service.create(quoteId, request);
    }

    /** El vendedor resuelve las solicitudes y las aplica de una vez; el cliente recibe el detalle. */
    @Post("/apply")
    public ChangeRequestDtos.ApplyResult apply(Long quoteId, @Valid @Body ChangeRequestDtos.ApplyRequest request,
                                               @Nullable Authentication auth) {
        String actor = auth != null ? auth.getName() : request.actor();
        return service.apply(quoteId, request, actor);
    }
}
