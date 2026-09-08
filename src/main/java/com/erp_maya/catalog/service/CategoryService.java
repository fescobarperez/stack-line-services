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
        c.setParentId(resolveParentId(req.parentId(), null));
        return toResponse(repository.save(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = find(id);
        c.setName(req.name());
        c.setIcon(req.icon());
        c.setParentId(resolveParentId(req.parentId(), id));
        return toResponse(repository.update(c));
    }

    @Transactional
    public CategoryResponse copy(Long id) {
        Category source = find(id);
        Category copy = copyNode(source, source.getParentId(), source.getName() + " (copia)");
        return toResponse(copy);
    }

    private Category copyNode(Category source, Long parentId, String name) {
        Category copy = new Category();
        copy.setCompanyId(tenant.getCompanyId());
        copy.setName(name);
        copy.setIcon(source.getIcon());
        copy.setParentId(parentId);
        Category saved = repository.save(copy);
        List<Category> children = repository.findByCompanyIdAndParentId(tenant.getCompanyId(), source.getId());
        for (Category child : children) {
            copyNode(child, saved.getId(), child.getName());
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Long resolveParentId(Long parentId, Long categoryId) {
        if (parentId == null) return null;
        if (categoryId != null && parentId.equals(categoryId)) {
            throw new IllegalStateException("Una categoría no puede ser su propia subcategoría");
        }
        Category parent = find(parentId);
        Long cursor = parent.getParentId();
        while (cursor != null) {
            if (categoryId != null && cursor.equals(categoryId)) {
                throw new IllegalStateException("No puedes mover una categoría dentro de una subcategoría propia");
            }
            cursor = find(cursor).getParentId();
        }
        return parent.getId();
    }

    private Category find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría " + id + " no encontrada"));
    }

    private static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getIcon(), c.getParentId());
    }
}
