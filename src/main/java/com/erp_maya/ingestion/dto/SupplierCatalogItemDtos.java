package com.erp_maya.ingestion.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;

public final class SupplierCatalogItemDtos {

    private SupplierCatalogItemDtos() {}

    @Serdeable
    public record Response(Long id, Long supplierId, String supplierName, Long productId,
                           String sourceProductName, List<String> sourceCategoryPath,
                           String sourceListingUrl, String productUrl, String supplierSku,
                           String brand, String manufacturerName, BigDecimal observedUnitCost,
                           String observedCurrency, String observedUnit, BigDecimal observedPackSize) {}
}
