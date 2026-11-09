package com.erp_maya.quote.controller;

import com.erp_maya.quote.dto.QuoteDesignDtos;
import com.erp_maya.quote.service.QuoteDesignService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

/**
 * Diseño del cliente en una cotización: lo guarda el asistente al aprobarse
 * el render y lo consulta el asesor en el detalle.
 */
@Controller("/api/quotes")
public class QuoteDesignController {

    private final QuoteDesignService designs;

    public QuoteDesignController(QuoteDesignService designs) {
        this.designs = designs;
    }

    @Put("/{id}/design")
    public QuoteDesignDtos.Response save(Long id, @Valid @Body QuoteDesignDtos.Save request,
                                         @Nullable Authentication auth) {
        return designs.save(id, request, auth != null ? auth.getName() : null);
    }

    /** 404 si la cotización no tiene diseño (lo normal en la mayoría). */
    @Get("/{id}/design")
    public HttpResponse<QuoteDesignDtos.Response> get(Long id) {
        return designs.get(id).map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
    }

    @Get("/{id}/design/image")
    public HttpResponse<byte[]> image(Long id) {
        return designs.image(id)
                .map(img -> HttpResponse.ok(img.bytes())
                        .contentType(img.mime())
                        .header("Cache-Control", "private, max-age=300"))
                .orElseGet(HttpResponse::notFound);
    }
}
