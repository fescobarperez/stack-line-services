package com.erp_maya.stockcount.controller;

import com.erp_maya.stockcount.dto.StockCountDtos;
import com.erp_maya.stockcount.service.StockCountService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/stock-counts")
public class StockCountController {

    private final StockCountService service;

    public StockCountController(StockCountService service) {
        this.service = service;
    }

    @Get
    public Page<StockCountDtos.Response> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Get("/{id}")
    public StockCountDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public StockCountDtos.Response create(@Valid @Body StockCountDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}/counts")
    public StockCountDtos.Response saveCounts(Long id, @Body StockCountDtos.CountUpdate request) {
        return service.saveCounts(id, request);
    }

    @Post("/{id}/close")
    public StockCountDtos.Response close(Long id) {
        return service.close(id);
    }
}
