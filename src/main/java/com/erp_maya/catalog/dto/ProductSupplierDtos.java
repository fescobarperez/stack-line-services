package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ProductSupplierDtos {

    @Serdeable
    public record Request(@NotNull Long supplierId,
                          @NotNull @DecimalMin("0.01") BigDecimal unitCost,
                          Boolean preferred) {}
}
