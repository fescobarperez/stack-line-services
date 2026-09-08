package com.erp_maya.project.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Construcción de una cotización a partir de materiales del proyecto.
 *
 * Cada línea agrupa 1..N materiales del pool disponible del proyecto. El
 * cliente solo ve la descripción y el precio; los materiales quedan internos y
 * su costo se calcula desde los snapshots. Al crear la cotización, cada
 * material queda estampado con quote_id/quote_item_id y sale del pool.
 */
public final class ProjectQuoteBuilderDtos {

    private ProjectQuoteBuilderDtos() {}

    /**
     * Una línea comercial compuesta.
     * - description: lo que ve el cliente. Si viene vacío y sourceGroupId trae
     *   carpeta, se usa el nombre de la carpeta como default.
     * - sellPrice: precio de venta de la línea (lo fija el usuario).
     * - materialIds: materiales del proyecto que componen la línea (deben estar
     *   disponibles: quote_id IS NULL).
     */
    @Serdeable
    public record LineRequest(String description, Long sourceGroupId, String uom,
                              @NotNull BigDecimal sellPrice,
                              @NotEmpty List<Long> materialIds) {}

    @Serdeable
    public record Request(LocalDate quoteDate, LocalDate validUntil, String notes, String createdBy,
                          String profitCalcType, BigDecimal profitValue,
                          @NotEmpty @Valid List<LineRequest> lines) {}
}
