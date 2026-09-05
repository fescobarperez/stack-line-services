package com.erp_maya.catalog.controller;

import com.erp_maya.catalog.dto.CategoryRequest;
import com.erp_maya.catalog.dto.CategoryResponse;
import com.erp_maya.catalog.service.CategoryService;
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

@Controller("/api/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @Get
    public List<CategoryResponse> list() {
        return service.list();
    }

    @Get("/{id}")
    public CategoryResponse get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @Body CategoryRequest request) {
        return service.create(request);
    }

    @Put("/{id}")
    public CategoryResponse update(Long id, @Valid @Body CategoryRequest request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
