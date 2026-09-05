package com.erp_maya.uom.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class UomDtos {

    private UomDtos() {}

    @Serdeable
    public record UnitRequest(@NotBlank String code, @NotBlank String name, String symbol,
                              String uomType, Boolean isBase, Boolean active) {}

    @Serdeable
    public record UnitResponse(Long id, String code, String name, String symbol, String uomType,
                               Boolean isBase, Boolean active) {}

    @Serdeable
    public record ConversionRequest(@NotNull Long fromUomId, @NotNull Long toUomId, @NotNull BigDecimal factor) {}

    @Serdeable
    public record ConversionResponse(Long id, Long fromUomId, String fromUomCode,
                                     Long toUomId, String toUomCode, BigDecimal factor) {}
}
