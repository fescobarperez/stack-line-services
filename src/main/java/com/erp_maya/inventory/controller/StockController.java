package com.erp_maya.inventory.controller;

import com.erp_maya.inventory.dto.StockDtos;
import com.erp_maya.inventory.service.StockService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/stock")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @Get
    public List<StockDtos.StockRow> stock(@Nullable @QueryValue Long branchId,
                                          @Nullable @QueryValue Long productId) {
        return service.listStock(branchId, productId);
    }

    @Get("/movements")
    public Page<StockDtos.Movement> movements(@Nullable @QueryValue Long productId, Pageable pageable) {
        return service.listMovements(productId, pageable);
    }

    @Post("/adjustments")
    @Status(HttpStatus.CREATED)
    public StockDtos.Movement adjust(@Valid @Body StockDtos.AdjustmentRequest request) {
        return service.adjust(request);
    }
}
