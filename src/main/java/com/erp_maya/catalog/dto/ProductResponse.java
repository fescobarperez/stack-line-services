package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;

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
        BigDecimal minStock,
        String status
) {
}
