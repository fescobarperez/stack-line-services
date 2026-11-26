package com.erp_maya.quote.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
     *   profitAmount    ganancia fija o porcentaje sobre la base de ganancia:
     *                   operatingCost ('total_cost', por defecto) o solo el
     *                   subtotal ('direct_cost'), según quotes.profit_base
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
                          List<ChargeResponse> charges,
                          /** Sobre qué se calculó la ganancia porcentual y con qué modo
                           *  ('total_cost' = operatingCost, 'direct_cost' = subtotal). */
                          BigDecimal profitBase, String profitBaseMode,
                          /** Lo que movió el redondeo automático del total (0 sin redondeo). */
                          BigDecimal roundingAdjustment) {}

    /**
     * Precios tal como los ve el cliente: ganancia, gastos operativos, cargos,
     * ajuste y redondeo ya van DENTRO del precio de cada producto, repartidos
     * en proporción a su precio. Las líneas suman exactamente el subtotal.
     */
    @Serdeable
    public record ClientPrices(BigDecimal subtotal, BigDecimal tax, BigDecimal total, List<ClientLine> lines) {}

    /** Una línea con su parte de los cargos. Null en las ocultas al cliente; 0 en las que cotiza un asesor. */
    @Serdeable
    public record ClientLine(Long itemId, BigDecimal unitPrice, BigDecimal lineTotal) {}

    /** Productos aún sin cotización (en espera): `key` lo pone quien pregunta; `lineTotal` = cantidad × precio de lista. */
    @Serdeable
    public record PreviewRequest(List<PreviewLine> lines) {}

    /**
     * Con `productId` y medidas, el ERP aplica el precio por medida como al
     * cotizar (fuera de la tolerancia, la línea queda en 0: la cotiza un asesor).
     *
     * Con `templateId`, la línea se calcula con la plantilla del producto
     * (params, choices, preset) como al agregarla a una cotización: el costo de
     * su materia prima es el precio base y `lineTotal` se ignora. Si a alguna
     * materia prima le falta costo, la línea queda en 0 (la cotiza un asesor).
     */
    @Serdeable
    public record PreviewLine(String key, BigDecimal quantity, BigDecimal lineTotal,
                              @io.micronaut.core.annotation.Nullable Long productId,
                              @io.micronaut.core.annotation.Nullable BigDecimal height,
                              @io.micronaut.core.annotation.Nullable BigDecimal width,
                              @io.micronaut.core.annotation.Nullable BigDecimal depth,
                              @io.micronaut.core.annotation.Nullable Long templateId,
                              @io.micronaut.core.annotation.Nullable Map<String, BigDecimal> params,
                              @io.micronaut.core.annotation.Nullable Map<String, String> choices,
                              @io.micronaut.core.annotation.Nullable String preset) {
        public PreviewLine(String key, BigDecimal quantity, BigDecimal lineTotal) {
            this(key, quantity, lineTotal, null, null, null, null);
        }

        public PreviewLine(String key, BigDecimal quantity, BigDecimal lineTotal, Long productId,
                           BigDecimal height, BigDecimal width, BigDecimal depth) {
            this(key, quantity, lineTotal, productId, height, width, depth, null, null, null, null);
        }
    }

    /** Vista previa: lo que costaría con los cargos, ganancia, IVA y redondeo de la empresa. */
    @Serdeable
    public record PreviewPrices(BigDecimal subtotal, BigDecimal tax, BigDecimal total, List<PreviewPrice> lines) {}

    /** `review`: la medida se sale de la tolerancia y el precio lo pone un asesor. */
    @Serdeable
    public record PreviewPrice(String key, BigDecimal unitPrice, BigDecimal lineTotal, boolean review) {}

    /**
     * Varias vistas previas en una sola consulta (el planificador valora una
     * opción y sus variantes). Cada escenario se valora por separado, con sus
     * propios cargos: un cargo fijo se cobra en cada uno, no se reparte.
     */
    @Serdeable
    public record PreviewBatchRequest(List<PreviewScenario> scenarios) {}

    @Serdeable
    public record PreviewScenario(String key, List<PreviewLine> lines) {}

    @Serdeable
    public record PreviewBatchResponse(List<ScenarioPrices> scenarios) {}

    /** Los precios de un escenario; si no se pudo valorar, montos nulos y `error` dice por qué. */
    @Serdeable
    public record ScenarioPrices(String key,
                                 @io.micronaut.core.annotation.Nullable BigDecimal subtotal,
                                 @io.micronaut.core.annotation.Nullable BigDecimal tax,
                                 @io.micronaut.core.annotation.Nullable BigDecimal total,
                                 List<PreviewPrice> lines,
                                 @io.micronaut.core.annotation.Nullable String error) {}
}
