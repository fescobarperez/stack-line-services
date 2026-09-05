package com.erp_maya.marketing.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PromotionDtos {

    private PromotionDtos() {}

    @Serdeable
    public record Request(@NotBlank String name, String promoType, String status,
                          BigDecimal value, String category, String product, String clientType, String branches,
                          String days, String horaInicio, String horaFin, BigDecimal minCompra,
                          Integer nxmN, Integer nxmM, LocalDate dateStart, LocalDate dateEnd, String description) {}

    @Serdeable
    public record Response(Long id, String name, String promoType, String status,
                           BigDecimal value, String category, String product, String clientType, String branches,
                           String days, String horaInicio, String horaFin, BigDecimal minCompra,
                           Integer nxmN, Integer nxmM, LocalDate dateStart, LocalDate dateEnd, String description,
                           Integer uses, BigDecimal savings, Integer tickets) {}

    /** Registro de una aplicación de la promoción (tracking, p.ej. desde POS). */
    @Serdeable
    public record UsageRequest(Long saleId, BigDecimal amountSaved, String reference) {}
}
