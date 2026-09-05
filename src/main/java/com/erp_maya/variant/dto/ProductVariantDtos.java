package com.erp_maya.variant.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class ProductVariantDtos {

    private ProductVariantDtos() {}

    @Serdeable
    public record Request(@NotNull Long productId, String attributeType, String attributeValue,
                          String sku, BigDecimal price, BigDecimal cost, Integer stock, Integer minStock,
                          Boolean active) {}

    @Serdeable
    public record Response(Long id, Long productId, String productName, String attributeType,
                           String attributeValue, String sku, BigDecimal price, BigDecimal cost,
                           Integer stock, Integer minStock, Boolean active) {}
}
