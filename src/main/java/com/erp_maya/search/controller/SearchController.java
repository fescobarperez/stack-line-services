package com.erp_maya.search.controller;

import com.erp_maya.search.dto.SearchDtos.SearchResponse;
import com.erp_maya.search.service.SearchService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

/** Búsqueda global (⌘K). */
@Controller("/api/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @Get
    public SearchResponse search(@Nullable @QueryValue String q) {
        return service.search(q);
    }
}
