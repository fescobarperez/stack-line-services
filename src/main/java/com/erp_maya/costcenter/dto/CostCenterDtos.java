package com.erp_maya.costcenter.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public final class CostCenterDtos {

    private CostCenterDtos() {}

    @Serdeable
    public record Request(@NotBlank String code, @NotBlank String name, String costGroup,
                          String centerType, Long responsibleUserId, BigDecimal budget, Boolean active) {}

    @Serdeable
    public record Response(Long id, String code, String name, String costGroup, String centerType,
                           Long responsibleUserId, String responsibleName, BigDecimal budget, Boolean active) {}
}
