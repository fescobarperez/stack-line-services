package com.erp_maya.quote.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Mantenimiento de condiciones comerciales (Configuración → Empresa → Cotizaciones). */
public final class QuoteConditionDtos {

    private QuoteConditionDtos() {}

    /**
     * sortOrder nulo = al final; active nulo = activa. perQuote: la define el
     * asesor en cada cotización y body es su valor por defecto.
     */
    @Serdeable
    public record Request(@NotBlank @Size(max = 80) String title,
                          @NotBlank @Size(max = 1000) String body,
                          Integer sortOrder, Boolean active, Boolean perQuote) {}

    @Serdeable
    public record Response(Long id, String title, String body, int sortOrder, boolean active, boolean perQuote) {}

    /** Una condición que define el asesor, con el valor de esta cotización. */
    @Serdeable
    public record QuoteValue(Long conditionId, String title, String value, String defaultValue) {}

    @Serdeable
    public record QuoteValueInput(@NotNull Long conditionId, @NotBlank @Size(max = 1000) String value) {}

    @Serdeable
    public record QuoteValuesRequest(@NotNull @Valid List<QuoteValueInput> values) {}
}
