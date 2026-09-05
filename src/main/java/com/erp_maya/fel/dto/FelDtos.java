package com.erp_maya.fel.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class FelDtos {

    private FelDtos() {}

    /** Certifica el DTE de una venta ante SAT (simulado). */
    @Serdeable
    public record CertifyRequest(@NotNull Long saleId, String dteType, String series) {}

    @Serdeable
    public record Response(Long id, Long saleId, String dteType, String series, String number,
                           String uuid, String authorizationNumber, String receptorName, String receptorNit,
                           BigDecimal taxableAmount, BigDecimal exemptAmount, BigDecimal tax, BigDecimal total,
                           String status, Instant issuedAt, Instant certifiedAt) {}
}
