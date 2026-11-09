package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
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
        List<SupplierCostResponse> suppliers,
        BigDecimal height,
        BigDecimal width,
        BigDecimal depth,
        String dimensionUnit,
        /** manual · assistant. */
        String origin,
        /** NULL (normal) · pending · discarded. */
        String reviewStatus,
        String reviewNotes,
        Instant createdAt,
        /** Nulo sin foto; si no, va en ?v= de GET /api/products/{id}/image. */
        Long imageVersion
) {

    @Serdeable
    public record SupplierCostResponse(Long supplierId, String supplierName,
                                       BigDecimal unitCost, Boolean preferred) {}
}
