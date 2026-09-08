package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

@Serdeable
public record ProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        Long categoryId,
        BigDecimal price,
        BigDecimal cost,
        String unit,
        String purchaseUnit,
        BigDecimal purchaseFactor,
        String itemType,
        Boolean tracksStock,
        BigDecimal minStock,
        String status,
        Long supplierId,
        BigDecimal supplierCost
) {
}
