package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;

@Serdeable
public record ProductResponse(
        Long id,
        String sku,
        String name,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        BigDecimal cost,
        BigDecimal avgCost,
        String unit,
        String purchaseUnit,
        BigDecimal purchaseFactor,
        String itemType,
        Boolean tracksStock,
        BigDecimal minStock,
        String status,
        List<SupplierCostResponse> suppliers
) {

    @Serdeable
    public record SupplierCostResponse(Long supplierId, String supplierName,
                                       BigDecimal unitCost, Boolean preferred) {}
}
