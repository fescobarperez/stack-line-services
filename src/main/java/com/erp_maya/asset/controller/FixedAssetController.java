package com.erp_maya.asset.controller;

import com.erp_maya.asset.dto.AssetDtos;
import com.erp_maya.asset.service.AssetService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

@Controller("/api/fixed-assets")
public class FixedAssetController {

    private final AssetService service;

    public FixedAssetController(AssetService service) {
        this.service = service;
    }

    @Get
    public List<AssetDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public AssetDtos.Response get(Long id) {
        return service.get(id);
    }

    @Get("/{id}/depreciation")
    public List<AssetDtos.DepreciationResponse> depreciationHistory(Long id) {
        return service.depreciationHistory(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public AssetDtos.Response create(@Valid @Body AssetDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public AssetDtos.Response update(Long id, @Valid @Body AssetDtos.Request request) {
        return service.update(id, request);
    }

    @Post("/{id}/depreciate")
    public AssetDtos.DepreciationResponse depreciate(Long id, @Nullable @QueryValue LocalDate periodDate) {
        return service.depreciate(id, periodDate);
    }

    /** Da de baja el activo y genera la partida contable de disposición. */
    @Post("/{id}/dispose")
    public AssetDtos.Response dispose(Long id, @Body AssetDtos.DisposeRequest request) {
        return service.dispose(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
