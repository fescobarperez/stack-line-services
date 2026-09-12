package com.erp_maya.catalog.controller;

import com.erp_maya.catalog.dto.ProductCategoryRequest;
import com.erp_maya.catalog.dto.ProductRequest;
import com.erp_maya.catalog.dto.ProductResponse;
import com.erp_maya.catalog.dto.ProductSupplierDtos;
import com.erp_maya.catalog.service.ProductService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
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

@Controller("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @Get
    public Page<ProductResponse> list(@Nullable @QueryValue String search,
                                      @Nullable @QueryValue String itemType,
                                      Pageable pageable) {
        return service.list(search, itemType, pageable);
    }

    @Get("/{id}")
    public ProductResponse get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public ProductResponse create(@Valid @Body ProductRequest request) {
        return service.create(request);
    }

    @Post("/{id}/suppliers")
    @Status(HttpStatus.CREATED)
    public ProductResponse addSupplier(Long id, @Valid @Body ProductSupplierDtos.Request request) {
        return service.addSupplier(id, request);
    }

    /**
     * La relación se lee también desde el proveedor: qué productos vende.
     *
     * Vive aquí y no en SupplierController porque catalog ya depende de
     * partner; al revés se cerraría el ciclo entre los dos módulos.
     */
    @Get("/by-supplier/{supplierId}")
    public List<ProductResponse> listBySupplier(Long supplierId) {
        return service.listBySupplier(supplierId);
    }

    @Put("/{id}/suppliers/{supplierId}")
    public ProductResponse updateSupplier(Long id, Long supplierId,
                                          @Valid @Body ProductSupplierDtos.Update request) {
        return service.updateSupplier(id, supplierId, request);
    }

    @Delete("/{id}/suppliers/{supplierId}")
    public ProductResponse removeSupplier(Long id, Long supplierId) {
        return service.removeSupplier(id, supplierId);
    }

    @Put("/{id}/category")
    public ProductResponse moveToCategory(Long id, @Valid @Body ProductCategoryRequest request) {
        return service.moveToCategory(id, request.categoryId());
    }

    @Put("/{id}")
    public ProductResponse update(Long id, @Valid @Body ProductRequest request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
