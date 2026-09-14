package com.erp_maya.quote.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Gastos/cargos de la cotización y el resumen de totales calculado. */
public final class QuoteChargeDtos {

    private QuoteChargeDtos() {}

    /**
     * calcType: 'fixed' (value absoluto) | 'percent' (value % sobre subtotal costo).
     * `categoryId` apunta a charge_categories; nulo = cargo sin clasificar.
     */
    @Serdeable
    public record Request(Long categoryId, @NotBlank String description,
                          @NotNull String calcType, @NotNull BigDecimal value, Integer sortOrder) {}

    /** `operating` dice si esta fila suma al bloque de Gastos Operativos. */
    @Serdeable
    public record ChargeResponse(Long id, Long categoryId, String category, Boolean operating,
                                 String description, String calcType, BigDecimal value,
                                 BigDecimal computedAmount, Integer sortOrder) {}

    /** Ajuste de cierre sobre la base antes de IVA. Negativo = descuento. */
    @Serdeable
    public record AdjustmentRequest(@NotNull BigDecimal amount) {}

    /** Cambio de modo de captura del gasto operativo: 'single' | 'detailed'. */
    @Serdeable
    public record OperatingModeRequest(@NotBlank String mode) {}

    /** Porcentaje de gasto operativo sobre el subtotal (modo 'percent'). */
    @Serdeable
    public record OperatingPctRequest(@NotNull BigDecimal pct) {}

    /** Monto del gasto operativo cuando se captura como cifra única. */
    @Serdeable
    public record OperatingAmountRequest(@NotNull BigDecimal amount, String description) {}

    /**
     * Alta o edición de un concepto de gasto.
     *
     * El `code` no se pide: se deriva del nombre. Es un identificador interno y
     * hacérselo teclear al usuario solo abre la puerta a duplicados con distinta
     * grafía, que es justo el problema que este catálogo vino a resolver.
     */
    @Serdeable
    public record CategoryRequest(@NotBlank String name, Boolean operating,
                                  Integer sortOrder, String status) {}

    /** Una categoría del catálogo, para poblar el selector y el mantenimiento. */
    @Serdeable
    public record CategoryResponse(Long id, String code, String name, Boolean operating,
                                   Boolean protectedRow, Integer sortOrder, String status) {}

    /**
     * Resumen calculado de la cotización:
     *   subtotal        Σ(cantidad × precio_unitario) · la BASE de todo
     *   materialsCost   costo de los materiales (informativo: no entra al total)
     *   fixedTotal      Σ cargos fixed
     *   operatingExpenses  gasto operativo: por porcentaje o por partidas
     *   otherCharges    cargos que no son de categoría operativa
     *   subtotalCost    subtotal + operatingExpenses
     *   operatingCost   subtotalCost + otherCharges · base de la ganancia
     *   profitAmount    ganancia fija o porcentaje sobre operatingCost
     *   taxableSubtotal operatingCost + profitAmount  (base ANTES del ajuste)
     *   adjustedBase    max(0, taxableSubtotal + manualAdjustment)
     *   tax             adjustedBase × tasa
     *   total           adjustedBase + tax
     */
    @Serdeable
    public record Summary(BigDecimal materialsCost, BigDecimal fixedTotal,
                          BigDecimal operatingExpenses, String operatingExpenseMode,
                          BigDecimal operatingExpensePct, BigDecimal subtotal,
                          BigDecimal otherCharges,
                          BigDecimal subtotalCost, BigDecimal percentTotal,
                          BigDecimal operatingCost, String profitCalcType, BigDecimal profitValue,
                          BigDecimal profitAmount, BigDecimal taxableSubtotal,
                          BigDecimal manualAdjustment, BigDecimal adjustedBase,
                          BigDecimal taxRate, BigDecimal tax, BigDecimal total,
                          List<ChargeResponse> charges) {}
}
