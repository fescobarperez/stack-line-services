package com.erp_maya.ingestion.service;

import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.ingestion.domain.SupplierCatalogItem;
import com.erp_maya.ingestion.dto.SupplierCatalogItemDtos;
import com.erp_maya.ingestion.repository.SupplierCatalogItemRepository;
import com.erp_maya.partner.repository.SupplierRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class SupplierCatalogItemService {

    private final SupplierCatalogItemRepository items;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final TenantContext tenant;

    public SupplierCatalogItemService(SupplierCatalogItemRepository items,
                                      ProductRepository products,
                                      SupplierRepository suppliers,
                                      TenantContext tenant) {
        this.items = items;
        this.products = products;
        this.suppliers = suppliers;
        this.tenant = tenant;
    }

    @Transactional
    public List<SupplierCatalogItemDtos.Response> listByProduct(Long productId) {
        Long companyId = tenant.getCompanyId();
        products.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + productId + " no encontrado"));
        Map<Long, String> names = suppliers.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(supplier -> supplier.getId(), supplier -> supplier.getName(), (a, b) -> a));
        return items.findByCompanyIdAndProductIdOrderBySupplierId(companyId, productId).stream()
                .map(item -> toResponse(item, names.getOrDefault(item.getSupplierId(), "Proveedor " + item.getSupplierId())))
                .toList();
    }

    private static SupplierCatalogItemDtos.Response toResponse(SupplierCatalogItem item, String supplierName) {
        return new SupplierCatalogItemDtos.Response(item.getId(), item.getSupplierId(), supplierName,
                item.getProductId(), item.getSourceProductName(), item.getSourceCategoryPath(),
                item.getSourceListingUrl(), item.getProductUrl(), item.getSupplierSku(), item.getBrand(),
                item.getManufacturerName(), item.getObservedUnitCost(), item.getObservedCurrency(),
                item.getObservedUnit(), item.getObservedPackSize());
    }
}
