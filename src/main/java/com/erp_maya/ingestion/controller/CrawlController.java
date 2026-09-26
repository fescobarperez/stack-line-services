package com.erp_maya.ingestion.controller;

import com.erp_maya.ingestion.dto.CrawlDtos;
import com.erp_maya.ingestion.service.CrawlService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;

@Controller("/api/supplier-websites")
public class CrawlController {
    private final CrawlService service;

    public CrawlController(CrawlService service) { this.service = service; }

    @Post("/{id}/crawl")
    public CrawlDtos.Response start(Long id, @Valid @Body CrawlDtos.StartRequest request) {
        return service.start(id, request);
    }
}
