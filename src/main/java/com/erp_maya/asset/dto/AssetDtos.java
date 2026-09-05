package com.erp_maya.asset.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class AssetDtos {

    private AssetDtos() {}

    @Serdeable
    public record Request(@NotBlank String assetCode, @NotBlank String name, String category,
                          BigDecimal purchaseCost, LocalDate acquiredDate, String serial, Long branchId,
                          String status, BigDecimal depreciationRate, Integer usefulLifeYears, String notes) {}

    @Serdeable
    public record Response(Long id, String assetCode, String name, String category, BigDecimal purchaseCost,
                           LocalDate acquiredDate, String serial, Long branchId, String status,
                           BigDecimal depreciationRate, Integer usefulLifeYears,
                           BigDecimal accumulatedDepreciation, BigDecimal bookValue,
                           LocalDate disposalDate, Long disposalJournalEntryId, String notes) {}

    /**
     * Baja del activo. La partida contable es automática: usa las cuentas
     * configuradas en Configuración (company_settings). No se eligen por baja.
     */
    @Serdeable
    public record DisposeRequest(LocalDate disposalDate, String notes) {}

    @Serdeable
    public record DepreciationResponse(Long id, LocalDate periodDate, BigDecimal depreciationAmount,
                                       BigDecimal accumulatedAmount, BigDecimal bookValue) {}
}
