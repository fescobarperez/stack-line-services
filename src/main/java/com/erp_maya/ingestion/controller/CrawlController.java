package com.erp_maya.ingestion.controller;

import com.erp_maya.ingestion.dto.CatalogImportDtos;
import com.erp_maya.ingestion.dto.CrawlDtos;
import com.erp_maya.ingestion.service.CrawlingOrchestrationService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;

@Controller("/api/supplier-websites")
public class CrawlController {
    private final CrawlingOrchestrationService service;

    public CrawlController(CrawlingOrchestrationService service) { this.service = service; }

    @Post("/{id}/crawl")
    public CrawlDtos.Response start(Long id, @Valid @Body CrawlDtos.StartRequest request) {
        return service.start(id, request);
    }

    @Post("/{id}/categories/discover")
    public CrawlDtos.DiscoveryResponse discover(Long id) {
        return service.startDiscovery(id);
    }

    @io.micronaut.http.annotation.Get("/{id}/categories/discover/{runId}")
    public CrawlDtos.DiscoveryResponse discovery(Long id, String runId) {
        return service.discovery(id, runId);
    }

    @Post("/{id}/categories/discover/{runId}/selection")
    public CatalogImportDtos.BatchResponse saveCategorySelection(Long id, String runId,
                                                                   @Valid @Body CatalogImportDtos.CategorySelectionRequest request) {
        return service.saveCategorySelection(id, request.categoryScope(), request.categoryTree());
    }

    @io.micronaut.http.annotation.Get("/runs/{runId}")
    public CrawlDtos.Response get(Long runId) {
        return service.get(runId);
    }
}
