package com.erp_maya.quote.controller;

import com.erp_maya.quote.dto.QuoteConditionDtos;
import com.erp_maya.quote.service.QuoteConditionService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Condiciones comerciales que define el asesor en una cotización (p. ej.
 * tiempo de entrega): se editan en «Editar borrador».
 */
@Controller("/api/quotes")
public class QuoteConditionValueController {

    private final QuoteConditionService service;

    public QuoteConditionValueController(QuoteConditionService service) {
        this.service = service;
    }

    @Get("/{id}/conditions")
    public List<QuoteConditionDtos.QuoteValue> list(Long id) {
        return service.deCotizacion(id);
    }

    @Put("/{id}/conditions")
    public List<QuoteConditionDtos.QuoteValue> save(Long id, @Valid @Body QuoteConditionDtos.QuoteValuesRequest request) {
        return service.guardarDeCotizacion(id, request);
    }
}
