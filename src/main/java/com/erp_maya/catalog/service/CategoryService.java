package com.erp_maya.catalog.service;

import com.erp_maya.catalog.domain.Category;
import com.erp_maya.catalog.dto.CategoryRequest;
import com.erp_maya.catalog.dto.CategoryResponse;
import com.erp_maya.catalog.repository.CategoryRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

/** Lógica de negocio de categorías. Todo queda acotado al inquilino actual. */
@Singleton
public class CategoryService {

    private final CategoryRepository repository;
    private final TenantContext tenant;

    public CategoryService(CategoryRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public List<CategoryResponse> list() {
        return repository.findByCompanyId(tenant.getCompanyId())
                .stream().map(CategoryService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        Category c = new Category();
        c.setCompanyId(tenant.getCompanyId());
        c.setName(req.name());
        c.setIcon(req.icon());
        return toResponse(repository.save(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = find(id);
        c.setName(req.name());
        c.setIcon(req.icon());
        return toResponse(repository.update(c));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Category find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría " + id + " no encontrada"));
    }

    private static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getIcon());
    }
}
