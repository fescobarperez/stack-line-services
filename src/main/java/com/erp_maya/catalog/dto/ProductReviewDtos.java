package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Productos que propone el asistente y su revisión por un asesor. */
public final class ProductReviewDtos {

    private ProductReviewDtos() {
    }

    /**
     * Lo que manda el asistente (agents-services) cuando el cliente pide algo
     * que no está en el catálogo. Sin precio: lo pone el asesor al aprobar.
     *
     * @param category nombre de la categoría (línea de producto); se busca sin
     *                 distinguir mayúsculas ni tildes. Si no existe, queda sin categoría.
     * @param notes    la ficha de diseño: lo que el cliente pidió y vio en fotos.
     */
    @Serdeable
    public record Proposal(
            @NotBlank @Size(max = 200) String name,
            String category,
            @Size(max = 4000) String notes,
            BigDecimal height,
            BigDecimal width,
            BigDecimal depth,
            String dimensionUnit
    ) {
    }

    /**
     * Decisión del asesor.
     *
     * @param action     approve · discard
     * @param price      obligatorio al aprobar
     * @param categoryId opcional; si viene, reemplaza la categoría propuesta
     * @param sku        opcional; si viene, reemplaza el SKU provisional
     */
    @Serdeable
    public record Review(
            @NotBlank String action,
            BigDecimal price,
            Long categoryId,
            String sku
    ) {
        public static final String APPROVE = "approve";
        public static final String DISCARD = "discard";
    }

    /** Cuántos esperan revisión, para el aviso en el inventario. */
    @Serdeable
    public record PendingCount(long pending) {
    }
}
