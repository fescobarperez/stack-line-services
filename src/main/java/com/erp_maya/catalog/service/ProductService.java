package com.erp_maya.catalog.service;

import com.erp_maya.catalog.domain.Category;
import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.domain.ProductSupplier;
import com.erp_maya.catalog.domain.ProductSupplierPrice;
import com.erp_maya.catalog.dto.ProductRequest;
import com.erp_maya.catalog.dto.ProductResponse;
import com.erp_maya.catalog.dto.ProductSupplierDtos;
import com.erp_maya.catalog.repository.CategoryRepository;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.catalog.repository.ProductSupplierPriceRepository;
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
import java.time.Instant;
import java.util.List;

/** Lógica de negocio de productos. Todo queda acotado al inquilino actual. */
@Singleton
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductSupplierRepository productSuppliers;
    private final ProductSupplierPriceRepository priceHistory;
    private final SupplierRepository suppliers;
    private final TenantContext tenant;

    public ProductService(ProductRepository products, CategoryRepository categories,
                          ProductSupplierRepository productSuppliers,
                          ProductSupplierPriceRepository priceHistory, SupplierRepository suppliers,
                          TenantContext tenant) {
        this.products = products;
        this.categories = categories;
        this.productSuppliers = productSuppliers;
        this.priceHistory = priceHistory;
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
        registrarPrecio(relation, null);
        if (preferred || product.getCost() == null || product.getCost().signum() <= 0) {
            product.setCost(req.unitCost());
            products.update(product);
        }
        return toResponse(product);
    }

    /**
     * Cambia el costo o la preferencia de un proveedor ya asociado.
     *
     * Sin esto la relación era de solo alta: la única forma de mover un costo
     * era el PUT del producto, que solo alcanza al proveedor preferido.
     */
    @Transactional
    public ProductResponse updateSupplier(Long productId, Long supplierId, ProductSupplierDtos.Update req) {
        Product product = find(productId);
        Long companyId = tenant.getCompanyId();
        ProductSupplier relation = productSuppliers
                .findByCompanyIdAndProductIdAndSupplierId(companyId, productId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El proveedor " + supplierId + " no está asociado a este producto"));
        BigDecimal costoAnterior = relation.getUnitCost();
        relation.setUnitCost(req.unitCost());
        boolean preferred = req.preferred() != null ? req.preferred() : relation.getPreferred();
        if (preferred && !Boolean.TRUE.equals(relation.getPreferred())) {
            desmarcarPreferidos(companyId, productId, relation.getId());
        }
        relation.setPreferred(preferred);
        productSuppliers.save(relation);
        registrarPrecio(relation, costoAnterior);
        if (preferred) {
            product.setCost(req.unitCost());
            products.update(product);
        }
        return toResponse(product);
    }

    /**
     * Rompe la asociación producto–proveedor.
     *
     * Una materia prima no puede quedarse sin proveedor: es la misma invariante
     * que `syncSupplier` exige al crearla. Y si se va el preferido, otro toma
     * su lugar, porque el costo del producto sale de ahí.
     */
    @Transactional
    public ProductResponse removeSupplier(Long productId, Long supplierId) {
        Product product = find(productId);
        Long companyId = tenant.getCompanyId();
        ProductSupplier relation = productSuppliers
                .findByCompanyIdAndProductIdAndSupplierId(companyId, productId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El proveedor " + supplierId + " no está asociado a este producto"));
        List<ProductSupplier> restantes = productSuppliers
                .findByCompanyIdAndProductIdOrderByPreferredDesc(companyId, productId)
                .stream().filter(otra -> !otra.getId().equals(relation.getId())).toList();
        if (restantes.isEmpty() && "raw_material".equalsIgnoreCase(product.getItemType())) {
            throw new IllegalStateException("Una materia prima debe conservar al menos un proveedor");
        }
        boolean eraPreferido = Boolean.TRUE.equals(relation.getPreferred());
        productSuppliers.delete(relation);
        if (eraPreferido && !restantes.isEmpty()) {
            ProductSupplier sucesor = restantes.get(0);
            sucesor.setPreferred(true);
            productSuppliers.save(sucesor);
            product.setCost(sucesor.getUnitCost());
            products.update(product);
        }
        return toResponse(product);
    }

    /** Catálogo que vende un proveedor: la relación leída desde el otro extremo. */
    @Transactional
    public List<ProductResponse> listBySupplier(Long supplierId) {
        Long companyId = tenant.getCompanyId();
        suppliers.findByIdAndCompanyId(supplierId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + supplierId + " no encontrado"));
        return productSuppliers.findByCompanyIdAndSupplierIdOrderByProductId(companyId, supplierId)
                .stream()
                .map(relation -> products.findByIdAndCompanyId(relation.getProductId(), companyId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Deja constancia del precio de la relación: cierra el vigente y abre uno
     * nuevo. Sin cambio de costo no escribe nada, para no llenar el historial
     * de filas idénticas cuando solo se cambió el proveedor preferido.
     *
     * Si el precio vigente nació en este mismo instante —crear y corregir
     * dentro de la misma transacción— se corrige en el sitio en vez de
     * cerrarlo: la tabla exige valid_until > valid_from y con dos escrituras
     * simultáneas esa restricción reventaría.
     */
    private void registrarPrecio(ProductSupplier relation, BigDecimal costoAnterior) {
        BigDecimal nuevo = relation.getUnitCost();
        if (nuevo == null || nuevo.signum() <= 0) return;
        if (costoAnterior != null && costoAnterior.compareTo(nuevo) == 0) return;

        Instant ahora = Instant.now();
        List<ProductSupplierPrice> vigentes = priceHistory
                .findByCompanyIdAndProductSupplierIdAndValidUntilIsNull(
                        relation.getCompanyId(), relation.getId());
        for (ProductSupplierPrice vigente : vigentes) {
            if (!vigente.getValidFrom().isBefore(ahora)) {
                vigente.setUnitCost(nuevo);
                priceHistory.update(vigente);
                return;
            }
            vigente.setValidUntil(ahora);
            priceHistory.update(vigente);
        }
        ProductSupplierPrice fila = new ProductSupplierPrice();
        fila.setCompanyId(relation.getCompanyId());
        fila.setProductSupplierId(relation.getId());
        fila.setUnitCost(nuevo);
        fila.setValidFrom(ahora);
        priceHistory.save(fila);
    }

    private void desmarcarPreferidos(Long companyId, Long productId, Long exceptoId) {
        productSuppliers.findByCompanyIdAndProductIdOrderByPreferredDesc(companyId, productId).stream()
                .filter(otra -> !otra.getId().equals(exceptoId))
                .filter(otra -> Boolean.TRUE.equals(otra.getPreferred()))
                .forEach(otra -> { otra.setPreferred(false); productSuppliers.save(otra); });
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
        BigDecimal costoAnterior = newRelation ? null : relation.getUnitCost();
        relation.setCompanyId(companyId);
        relation.setProductId(product.getId());
        relation.setSupplierId(supplier.getId());
        relation.setUnitCost(req.supplierCost());
        if (newRelation) {
            relation.setPreferred(existing.isEmpty());
        }
        productSuppliers.save(relation);
        registrarPrecio(relation, costoAnterior);
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
