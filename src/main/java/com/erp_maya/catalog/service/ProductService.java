package com.erp_maya.catalog.service;

import com.erp_maya.catalog.domain.Category;
import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.domain.ProductSupplier;
import com.erp_maya.catalog.dto.ProductRequest;
import com.erp_maya.catalog.dto.ProductResponse;
import com.erp_maya.catalog.dto.ProductSupplierDtos;
import com.erp_maya.catalog.repository.CategoryRepository;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.catalog.repository.ProductSupplierRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.Supplier;
import com.erp_maya.partner.repository.SupplierRepository;
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
    private final ProductSupplierRepository productSuppliers;
    private final SupplierRepository suppliers;
    private final TenantContext tenant;

    public ProductService(ProductRepository products, CategoryRepository categories,
                          ProductSupplierRepository productSuppliers, SupplierRepository suppliers,
                          TenantContext tenant) {
        this.products = products;
        this.categories = categories;
        this.productSuppliers = productSuppliers;
        this.suppliers = suppliers;
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
        return page.map(this::toResponse);
    }

    @Transactional
    public ProductResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest req) {
        ensureSkuAvailable(req.sku(), null);
        Product p = new Product();
        p.setCompanyId(tenant.getCompanyId());
        apply(p, req);
        Product saved = products.save(p);
        syncSupplier(saved, req, true);
        Product updated = products.update(saved);
        return toResponse(updated);
    }

    @Transactional
    public ProductResponse addSupplier(Long productId, ProductSupplierDtos.Request req) {
        Product product = find(productId);
        Long companyId = tenant.getCompanyId();
        Supplier supplier = suppliers.findByIdAndCompanyId(req.supplierId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + req.supplierId() + " no encontrado"));
        List<ProductSupplier> existing = productSuppliers
                .findByCompanyIdAndProductIdOrderByPreferredDesc(companyId, productId);
        if (productSuppliers.findByCompanyIdAndProductIdAndSupplierId(companyId, productId, supplier.getId()).isPresent()) {
            throw new IllegalStateException("El proveedor ya está asociado a este producto");
        }
        boolean preferred = req.preferred() != null ? req.preferred() : existing.isEmpty();
        if (preferred) {
            existing.forEach(relation -> {
                relation.setPreferred(false);
                productSuppliers.save(relation);
            });
        }
        ProductSupplier relation = new ProductSupplier();
        relation.setCompanyId(companyId);
        relation.setProductId(productId);
        relation.setSupplierId(supplier.getId());
        relation.setUnitCost(req.unitCost());
        relation.setPreferred(preferred);
        productSuppliers.save(relation);
        if (preferred || product.getCost() == null || product.getCost().signum() <= 0) {
            product.setCost(req.unitCost());
            products.update(product);
        }
        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest req) {
        Product p = find(id);
        ensureSkuAvailable(req.sku(), id);
        apply(p, req);
        syncSupplier(p, req, false);
        Product updated = products.update(p);
        return toResponse(updated);
    }

    @Transactional
    public ProductResponse moveToCategory(Long id, Long categoryId) {
        Product product = find(id);
        product.setCategory(resolveCategory(categoryId));
        return toResponse(products.update(product));
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

    private void syncSupplier(Product product, ProductRequest req, boolean creating) {
        boolean rawMaterial = "raw_material".equalsIgnoreCase(product.getItemType());
        boolean hasSupplierData = req.supplierId() != null || req.supplierCost() != null;
        if (!rawMaterial && !hasSupplierData) {
            return;
        }
        if (req.supplierId() == null || req.supplierCost() == null || req.supplierCost().signum() <= 0) {
            if (creating || hasSupplierData) {
                throw new IllegalStateException("Una materia prima requiere proveedor y costo para ese proveedor");
            }
            return;
        }
        Long companyId = tenant.getCompanyId();
        Supplier supplier = suppliers.findByIdAndCompanyId(req.supplierId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + req.supplierId() + " no encontrado"));
        List<ProductSupplier> existing = productSuppliers
                .findByCompanyIdAndProductIdOrderByPreferredDesc(companyId, product.getId());
        ProductSupplier relation = productSuppliers
                .findByCompanyIdAndProductIdAndSupplierId(companyId, product.getId(), supplier.getId())
                .orElseGet(ProductSupplier::new);
        boolean newRelation = relation.getId() == null;
        relation.setCompanyId(companyId);
        relation.setProductId(product.getId());
        relation.setSupplierId(supplier.getId());
        relation.setUnitCost(req.supplierCost());
        if (newRelation) {
            relation.setPreferred(existing.isEmpty());
        }
        productSuppliers.save(relation);
        if (rawMaterial) {
            product.setCost(req.supplierCost());
        }
    }

    private void ensureSkuAvailable(String sku, Long productId) {
        products.findBySkuAndCompanyId(sku, tenant.getCompanyId())
                .filter(existing -> productId == null || !existing.getId().equals(productId))
                .ifPresent(existing -> {
                    throw new IllegalStateException("El SKU " + sku + " ya está registrado para otro producto");
                });
    }

    private ProductResponse.SupplierCostResponse toSupplierResponse(ProductSupplier relation) {
        Supplier supplier = suppliers.findByIdAndCompanyId(relation.getSupplierId(), relation.getCompanyId()).orElse(null);
        return new ProductResponse.SupplierCostResponse(
                relation.getSupplierId(), supplier == null ? null : supplier.getName(),
                relation.getUnitCost(), relation.getPreferred());
    }

    private ProductResponse toResponse(Product p) {
        Category c = p.getCategory();
        List<ProductResponse.SupplierCostResponse> supplierCosts = productSuppliers
                .findByCompanyIdAndProductIdOrderByPreferredDesc(p.getCompanyId(), p.getId())
                .stream().map(this::toSupplierResponse).toList();
        return new ProductResponse(
                p.getId(), p.getSku(), p.getName(),
                c != null ? c.getId() : null,
                c != null ? c.getName() : null,
                p.getPrice(), p.getCost(), p.getAvgCost(),
                p.getUnit(), p.getPurchaseUnit(), p.getPurchaseFactor(),
                p.getItemType(), p.getTracksStock(), p.getMinStock(), p.getStatus(), supplierCosts
        );
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

}
