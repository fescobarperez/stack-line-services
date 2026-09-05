package com.erp_maya.variant.controller;

import com.erp_maya.variant.dto.ProductVariantDtos;
import com.erp_maya.variant.service.ProductVariantService;
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

import java.util.List;

@Controller("/api/product-variants")
public class ProductVariantController {

    private final ProductVariantService service;

    public ProductVariantController(ProductVariantService service) {
        this.service = service;
    }

    @Get
    public List<ProductVariantDtos.Response> list(@Nullable @QueryValue Long productId) {
        return service.list(productId);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public ProductVariantDtos.Response create(@Valid @Body ProductVariantDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public ProductVariantDtos.Response update(Long id, @Valid @Body ProductVariantDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
