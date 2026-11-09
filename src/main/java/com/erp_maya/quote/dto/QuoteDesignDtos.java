package com.erp_maya.quote.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Diseño que el cliente aprobó con el asistente: ficha + render. */
public final class QuoteDesignDtos {

    private QuoteDesignDtos() {
    }

    /** La ficha de diseño, tal como la arma el asistente. */
    @Serdeable
    public record Spec(
            @Nullable String tipo,
            @Nullable String descripcion,
            @Nullable String material,
            @Nullable String acabado,
            @Nullable BigDecimal alto,
            @Nullable BigDecimal ancho,
            @Nullable BigDecimal profundidad,
            @Nullable Boolean medidasConfirmadas,
            @Nullable Map<String, String> detalles
    ) {
    }

    /**
     * Lo que manda el asistente al aprobarse el diseño. La imagen va en base64
     * (un render pesa 1–2 MB); si no viene, se guarda solo la ficha.
     */
    @Serdeable
    public record Save(
            @NotNull @Valid Spec spec,
            @Nullable Integer renderNumber,
            @Nullable Integer renderMax,
            @Nullable String imageBase64,
            @Nullable String imageMime
    ) {
    }

    @Serdeable
    public record Response(
            Long quoteId,
            Spec spec,
            @Nullable Integer renderNumber,
            @Nullable Integer renderMax,
            boolean hasImage,
            @Nullable String createdBy,
            Instant updatedAt
    ) {
    }
}
