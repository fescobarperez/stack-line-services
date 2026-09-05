package com.erp_maya.pos.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class CashRegisterDtos {

    private CashRegisterDtos() {}

    @Serdeable
    public record OpenRequest(@NotNull Long cashPointId, Long userId, BigDecimal openingAmount) {}

    @Serdeable
    public record CloseRequest(@NotNull BigDecimal closingAmount) {}

    @Serdeable
    public record Response(Long id, Long branchId, String branchName,
                           Long cashPointId, String cashPointCode, String cashPointName,
                           java.time.LocalDate businessDate, Long userId, String userName,
                           Instant openedAt, Instant closedAt, BigDecimal openingAmount, BigDecimal closingAmount,
                           BigDecimal salesTotal, BigDecimal salesCash, BigDecimal salesCard, BigDecimal refunds,
                           BigDecimal difference, String status) {}
}
