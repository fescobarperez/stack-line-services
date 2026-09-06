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

import java.math.BigDecimal;
import java.util.List;

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
        return list(search, null, pageable);
    }

    /**
     * `itemType` nulo devuelve todo; el POS pide 'sellable'.
     *
     * Lleva @Transactional propia: la llamada desde el otro `list` es interna y
     * no pasa por el proxy, así que sin esto no habría sesión y el Category
     * perezoso fallaría al mapear la respuesta.
     */
    @Transactional
    public Page<ProductResponse> list(String search, String itemType, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasType = itemType != null && !itemType.isBlank();
        Page<Product> page;
        if (hasType && hasSearch) {
            page = products.findByCompanyIdAndItemTypeAndNameContainsIgnoreCase(
                    companyId, itemType, search.trim(), pageable);
        } else if (hasType) {
            page = products.findByCompanyIdAndItemType(companyId, itemType, pageable);
        } else if (hasSearch) {
            page = products.findByCompanyIdAndNameContainsIgnoreCase(companyId, search.trim(), pageable);
        } else {
            page = products.findByCompanyId(companyId, pageable);
        }
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
        // Tipo de artículo; por omisión vendible, que es como se comportaba todo.
        String type = req.itemType() == null || req.itemType().isBlank() ? "sellable" : req.itemType();
        if (!List.of("sellable", "raw_material", "service").contains(type)) {
            throw new IllegalStateException("Tipo de artículo no válido: " + type);
        }
        p.setItemType(type);
        // Un servicio no lleva existencias salvo que se diga lo contrario.
        p.setTracksStock(req.tracksStock() != null ? req.tracksStock() : !"service".equals(type));
        // La columna es NOT NULL: sin este respaldo, crear un producto sin
        // mínimo revienta con violación de restricción (bug preexistente).
        p.setMinStock(req.minStock() != null ? req.minStock() : BigDecimal.ZERO);
        // Factor 1 = se compra como se guarda, que es el caso de casi todo.
        p.setPurchaseUnit(req.purchaseUnit());
        p.setPurchaseFactor(req.purchaseFactor() != null && req.purchaseFactor().signum() > 0
                ? req.purchaseFactor() : BigDecimal.ONE);
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
                p.getUnit(), p.getPurchaseUnit(), p.getPurchaseFactor(),
                p.getItemType(), p.getTracksStock(), p.getMinStock(), p.getStatus()
        );
    }
}
