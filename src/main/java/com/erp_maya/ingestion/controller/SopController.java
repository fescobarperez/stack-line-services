package com.erp_maya.ingestion.controller;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.ingestion.sop.SopDocument;
import com.erp_maya.ingestion.sop.SopRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/api/ingestion/sops")
public class SopController {

    private final SopRepository repository;

    public SopController(SopRepository repository) {
        this.repository = repository;
    }

    @Get("/{siteKey}")
    public SopDocument get(String siteKey) {
        return repository.findActive(siteKey)
                .orElseThrow(() -> new ResourceNotFoundException("SOP " + siteKey + " no encontrado"));
    }
}
