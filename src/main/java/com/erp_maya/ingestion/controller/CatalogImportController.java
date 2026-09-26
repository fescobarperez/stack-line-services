package com.erp_maya.ingestion.controller;

import com.erp_maya.ingestion.dto.CatalogImportDtos;
import com.erp_maya.ingestion.service.CatalogImportService;
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

@Controller("/api/catalog-imports")
public class CatalogImportController {

    private final CatalogImportService service;

    public CatalogImportController(CatalogImportService service) {
        this.service = service;
    }

    @Get
    public List<CatalogImportDtos.BatchResponse> list() {
        return service.list();
    }

    @Get("/{id}")
    public CatalogImportDtos.BatchResponse get(Long id) {
        return service.get(id);
    }

    @Post("/preview")
    @Status(HttpStatus.CREATED)
    public CatalogImportDtos.BatchResponse preview(@Valid @Body CatalogImportDtos.PreviewRequest request) {
        return service.preview(request);
    }


    @Delete("/{batchId}/items/{itemId}")
    public CatalogImportDtos.BatchResponse excludeItem(Long batchId, Long itemId) {
        return service.excludeItem(batchId, itemId);
    }
    @Put("/{batchId}/items/{itemId}/decision")
    public CatalogImportDtos.BatchResponse updateDecision(Long batchId, Long itemId,
                                                            @Valid @Body CatalogImportDtos.DecisionRequest request) {
        return service.updateDecision(batchId, itemId, request);
    }

    @Post("/{id}/approve")
    public CatalogImportDtos.BatchResponse approve(Long id, @Body CatalogImportDtos.SelectionRequest request) {
        return service.approve(id, request);
    }

    @Post("/{id}/reject")
    public CatalogImportDtos.BatchResponse reject(Long id, @Body CatalogImportDtos.SelectionRequest request) {
        return service.reject(id, request);
    }

    @Post("/{id}/apply")
    public CatalogImportDtos.BatchResponse apply(Long id) {
        return service.apply(id);
    }
}
