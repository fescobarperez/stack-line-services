package com.erp_maya.uom.controller;

import com.erp_maya.uom.dto.UomDtos;
import com.erp_maya.uom.service.UomService;
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

@Controller("/api/uom")
public class UomController {

    private final UomService service;

    public UomController(UomService service) {
        this.service = service;
    }

    @Get("/units")
    public List<UomDtos.UnitResponse> listUnits() {
        return service.listUnits();
    }

    @Post("/units")
    @Status(HttpStatus.CREATED)
    public UomDtos.UnitResponse createUnit(@Valid @Body UomDtos.UnitRequest request) {
        return service.createUnit(request);
    }

    @Put("/units/{id}")
    public UomDtos.UnitResponse updateUnit(Long id, @Valid @Body UomDtos.UnitRequest request) {
        return service.updateUnit(id, request);
    }

    @Delete("/units/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void deleteUnit(Long id) {
        service.deleteUnit(id);
    }

    @Get("/conversions")
    public List<UomDtos.ConversionResponse> listConversions() {
        return service.listConversions();
    }

    @Post("/conversions")
    @Status(HttpStatus.CREATED)
    public UomDtos.ConversionResponse createConversion(@Valid @Body UomDtos.ConversionRequest request) {
        return service.createConversion(request);
    }

    @Delete("/conversions/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void deleteConversion(Long id) {
        service.deleteConversion(id);
    }
}
