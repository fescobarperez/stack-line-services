package com.erp_maya.template.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Plantillas de cálculo. Se guardan completas (cabecera y partes) en una sola
 * petición; las partes se refieren entre sí por código, no por id, así la
 * pantalla no tiene que conocer ids que todavía no existen.
 */
public final class TemplateDtos {

    private TemplateDtos() {
    }

    @Serdeable
    public record Param(@NotBlank String code, @NotBlank String label, @Nullable String unit,
                        @Nullable BigDecimal min, @Nullable BigDecimal max, @Nullable BigDecimal defaultValue,
                        @Nullable Boolean required) {}

    @Serdeable
    public record Choice(@NotBlank String code, @NotBlank String label, @Nullable String renderText,
                         @Nullable String benefit, @Nullable Boolean isDefault) {}

    @Serdeable
    public record Option(@NotBlank String code, @NotBlank String label, @Nullable Boolean required,
                         @NotNull @Valid List<Choice> choices) {}

    /** option/choice vacíos = va siempre. productName/unit solo en la respuesta. */
    @Serdeable
    public record Component(@NotNull Long productId, @Nullable String productName, @Nullable String unit,
                            @Nullable String option, @Nullable String choice, @NotBlank String formula,
                            @Nullable BigDecimal wastePct, @Nullable Boolean roundUp, @Nullable String group) {}

    @Serdeable
    public record Preset(@NotBlank String code, @NotBlank String label, @NotNull Map<String, String> choices) {}

    @Serdeable
    public record Save(@NotNull Long productId, @NotBlank String name, @Nullable String description,
                       @Nullable Boolean active,
                       @NotNull @Valid List<Param> params,
                       @Nullable @Valid List<Option> options,
                       @NotNull @Valid List<Component> components,
                       @Nullable @Valid List<Preset> presets) {}

    @Serdeable
    public record Response(Long id, Long productId, String productName, String name, @Nullable String description,
                           boolean active, int version, List<Param> params, List<Option> options,
                           List<Component> components, List<Preset> presets,
                           @Nullable Instant updatedAt) {}

    @Serdeable
    public record Summary(Long id, Long productId, String productName, String name, boolean active, int version,
                          int params, int options, int components) {}

    /** preset = combinación armada; lo que venga en choices la ajusta. */
    @Serdeable
    public record CalculateRequest(@Nullable Map<String, BigDecimal> params, @Nullable Map<String, String> choices,
                                   @Nullable String preset) {}

    @Serdeable
    public record Line(Long productId, String productName, String unit, String group, BigDecimal quantity,
                       @Nullable Long supplierId, BigDecimal unitCost, BigDecimal amount, boolean missingCost) {}

    @Serdeable
    public record Calculation(Map<String, BigDecimal> params, Map<String, String> choices, List<Line> lines,
                              BigDecimal materialsCost, String description, String renderText, boolean missingCosts) {}

    /** Agregar o recalcular una línea de cotización con la plantilla del producto. */
    @Serdeable
    public record QuoteItemRequest(@NotNull Long templateId, @Nullable Map<String, BigDecimal> params,
                                   @Nullable Map<String, String> choices, @Nullable String preset,
                                   @Nullable BigDecimal quantity) {}
}
