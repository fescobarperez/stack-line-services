package com.erp_maya.variant.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.variant.domain.ProductVariant;
import com.erp_maya.variant.dto.ProductVariantDtos;
import com.erp_maya.variant.repository.ProductVariantRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class ProductVariantService {

    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final TenantContext tenant;

    public ProductVariantService(ProductVariantRepository variants, ProductRepository products, TenantContext tenant) {
        this.variants = variants;
        this.products = products;
        this.tenant = tenant;
    }

    @Transactional
    public List<ProductVariantDtos.Response> list(Long productId) {
        Long companyId = tenant.getCompanyId();
        List<ProductVariant> rows = (productId != null)
                ? variants.findByCompanyIdAndProductId(companyId, productId)
                : variants.findByCompanyId(companyId);
        return rows.stream().map(ProductVariantService::toResponse).toList();
    }

    @Transactional
    public ProductVariantDtos.Response create(ProductVariantDtos.Request req) {
        ProductVariant v = new ProductVariant();
        v.setCompanyId(tenant.getCompanyId());
        apply(v, req);
        return toResponse(variants.save(v));
    }

    @Transactional
    public ProductVariantDtos.Response update(Long id, ProductVariantDtos.Request req) {
        ProductVariant v = find(id);
        apply(v, req);
        return toResponse(variants.update(v));
    }

    @Transactional
    public void delete(Long id) {
        variants.delete(find(id));
    }

    private void apply(ProductVariant v, ProductVariantDtos.Request req) {
        Product product = products.findByIdAndCompanyId(req.productId(), tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + req.productId() + " no encontrado"));
        v.setProduct(product);
        v.setAttributeType(req.attributeType());
        v.setAttributeValue(req.attributeValue());
        v.setSku(req.sku());
        v.setPrice(req.price());
        v.setCost(req.cost() != null ? req.cost() : java.math.BigDecimal.ZERO);
        v.setStock(req.stock() != null ? req.stock() : 0);
        v.setMinStock(req.minStock() != null ? req.minStock() : 0);
        v.setActive(req.active() != null ? req.active() : Boolean.TRUE);
    }

    private ProductVariant find(Long id) {
        return variants.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Variante " + id + " no encontrada"));
    }

    private static ProductVariantDtos.Response toResponse(ProductVariant v) {
        Product p = v.getProduct();
        return new ProductVariantDtos.Response(v.getId(),
                p != null ? p.getId() : null, p != null ? p.getName() : null,
                v.getAttributeType(), v.getAttributeValue(), v.getSku(), v.getPrice(),
                v.getCost(), v.getStock(), v.getMinStock(), v.getActive());
    }
}
