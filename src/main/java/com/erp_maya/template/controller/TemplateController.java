package com.erp_maya.template.controller;

import com.erp_maya.quote.dto.QuoteDtos;
import com.erp_maya.template.dto.TemplateDtos;
import com.erp_maya.template.service.QuoteTemplateService;
import com.erp_maya.template.service.TemplateService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Plantillas de cálculo por producto y su uso en cotizaciones.
 *
 *   GET  /api/product-templates                      lista
 *   GET  /api/product-templates/{id}                 completa
 *   GET  /api/product-templates/by-product/{id}      la activa del producto (404 si no tiene)
 *   POST /api/product-templates                      alta
 *   PUT  /api/product-templates/{id}                 reemplazo (sube la versión)
 *   POST /api/product-templates/{id}/active          {active}
 *   POST /api/product-templates/{id}/calculate       cálculo sin guardar nada
 *   POST /api/quotes/{id}/template-items             agrega una línea calculada
 *   PUT  /api/quotes/{id}/template-items/{itemId}    la recalcula
 */
@Controller
public class TemplateController {

    private final TemplateService templates;
    private final QuoteTemplateService quoteTemplates;

    public TemplateController(TemplateService templates, QuoteTemplateService quoteTemplates) {
        this.templates = templates;
        this.quoteTemplates = quoteTemplates;
    }

    @Get("/api/product-templates")
    public List<TemplateDtos.Summary> list() {
        return templates.list();
    }

    @Get("/api/product-templates/{id}")
    public TemplateDtos.Response get(Long id) {
        return templates.get(id);
    }

    @Get("/api/product-templates/by-product/{productId}")
    public HttpResponse<TemplateDtos.Response> forProduct(Long productId) {
        return templates.forProduct(productId).map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
    }

    @Post("/api/product-templates")
    public HttpResponse<TemplateDtos.Response> create(@Valid @Body TemplateDtos.Save request) {
        return HttpResponse.created(templates.save(null, request));
    }

    @Put("/api/product-templates/{id}")
    public TemplateDtos.Response update(Long id, @Valid @Body TemplateDtos.Save request) {
        return templates.save(id, request);
    }

    @Post("/api/product-templates/{id}/active")
    public TemplateDtos.Response setActive(Long id, @Body ActiveRequest request) {
        return templates.setActive(id, request.active());
    }

    @Post("/api/product-templates/{id}/calculate")
    public TemplateDtos.Calculation calculate(Long id, @Body TemplateDtos.CalculateRequest request) {
        return templates.calculate(id, request);
    }

    @Post("/api/quotes/{id}/template-items")
    public QuoteDtos.Response addItem(Long id, @Valid @Body TemplateDtos.QuoteItemRequest request,
                                      @Nullable Authentication auth) {
        return quoteTemplates.addItem(id, request, auth != null ? auth.getName() : null);
    }

    @Put("/api/quotes/{id}/template-items/{itemId}")
    public QuoteDtos.Response recalculate(Long id, Long itemId, @Valid @Body TemplateDtos.QuoteItemRequest request,
                                          @Nullable Authentication auth) {
        return quoteTemplates.recalculate(id, itemId, request, auth != null ? auth.getName() : null);
    }

    @io.micronaut.serde.annotation.Serdeable
    public record ActiveRequest(boolean active) {}
}
