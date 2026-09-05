package com.erp_maya.catalog.service;

import com.erp_maya.catalog.domain.Category;
import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.dto.ProductRequest;
import com.erp_maya.catalog.dto.ProductResponse;
import com.erp_maya.catalog.repository.CategoryRepository;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

/** Lógica de negocio de productos. Todo queda acotado al inquilino actual. */
@Singleton
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final TenantContext tenant;

    public ProductService(ProductRepository products, CategoryRepository categories, TenantContext tenant) {
        this.products = products;
        this.categories = categories;
        this.tenant = tenant;
    }

    @Transactional
    public Page<ProductResponse> list(String search, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<Product> page = (search == null || search.isBlank())
                ? products.findByCompanyId(companyId, pageable)
                : products.findByCompanyIdAndNameContainsIgnoreCase(companyId, search.trim(), pageable);
        return page.map(ProductService::toResponse);
    }

    @Transactional
    public ProductResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest req) {
        Product p = new Product();
        p.setCompanyId(tenant.getCompanyId());
        apply(p, req);
        return toResponse(products.save(p));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest req) {
        Product p = find(id);
        apply(p, req);
        return toResponse(products.update(p));
    }

    @Transactional
    public void delete(Long id) {
        products.delete(find(id));
    }

    private void apply(Product p, ProductRequest req) {
        p.setSku(req.sku());
        p.setName(req.name());
        p.setPrice(req.price());
        p.setCost(req.cost());
        p.setUnit(req.unit());
        p.setMinStock(req.minStock());
        p.setStatus(req.status() != null ? req.status() : "active");
        p.setCategory(resolveCategory(req.categoryId()));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findByIdAndCompanyId(categoryId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría " + categoryId + " no encontrada"));
    }

    private Product find(Long id) {
        return products.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + id + " no encontrado"));
    }

    private static ProductResponse toResponse(Product p) {
        Category c = p.getCategory();
        return new ProductResponse(
                p.getId(), p.getSku(), p.getName(),
                c != null ? c.getId() : null,
                c != null ? c.getName() : null,
                p.getPrice(), p.getCost(), p.getAvgCost(),
                p.getUnit(), p.getMinStock(), p.getStatus()
        );
    }
}
