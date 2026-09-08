package com.erp_maya.quote.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Gastos/cargos de la cotización y el resumen de totales calculado. */
public final class QuoteChargeDtos {

    private QuoteChargeDtos() {}

    /** calcType: 'fixed' (value absoluto) | 'percent' (value % sobre subtotal costo). */
    @Serdeable
    public record Request(String category, @NotBlank String description,
                          @NotNull String calcType, @NotNull BigDecimal value, Integer sortOrder) {}

    @Serdeable
    public record ChargeResponse(Long id, String category, String description,
                                 String calcType, BigDecimal value, BigDecimal computedAmount,
                                 Integer sortOrder) {}

    /**
     * Resumen calculado de la cotización:
     *   materialsCost   Σ costo de materiales de las líneas (derivado, no persistido)
     *   fixedTotal      Σ cargos fixed
     *   subtotalCost    materialsCost + fixedTotal (base de los percent)
     *   operatingCost   subtotalCost + percentTotal
     *   profitAmount    ganancia fija o porcentaje sobre operatingCost
     *   taxableSubtotal operatingCost + profitAmount
     *   tax             taxableSubtotal × tasa
     *   total           taxableSubtotal + tax
     */
    @Serdeable
    public record Summary(BigDecimal materialsCost, BigDecimal fixedTotal,
                          BigDecimal subtotalCost, BigDecimal percentTotal,
                          BigDecimal operatingCost, String profitCalcType, BigDecimal profitValue,
                          BigDecimal profitAmount, BigDecimal taxableSubtotal,
                          BigDecimal taxRate, BigDecimal tax, BigDecimal total,
                          List<ChargeResponse> charges) {}
}
