package com.erp_maya.quote.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mantenimiento de condiciones comerciales (Configuración → Empresa → Cotizaciones). */
public final class QuoteConditionDtos {

    private QuoteConditionDtos() {}

    /** sortOrder nulo = al final; active nulo = activa. */
    @Serdeable
    public record Request(@NotBlank @Size(max = 80) String title,
                          @NotBlank @Size(max = 1000) String body,
                          Integer sortOrder, Boolean active) {}

    @Serdeable
    public record Response(Long id, String title, String body, int sortOrder, boolean active) {}
}
