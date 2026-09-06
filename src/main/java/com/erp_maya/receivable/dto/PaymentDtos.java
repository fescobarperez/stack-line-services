package com.erp_maya.receivable.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PaymentDtos {

    private PaymentDtos() {}

    @Serdeable
    public record Request(@NotNull Long clientId, Long saleId, Long projectId, @NotNull BigDecimal amount,
                          LocalDate paymentDate, String method, String reference, String notes) {}

    @Serdeable
    public record Response(Long id, Long clientId, String clientName, Long saleId, Long projectId,
                           BigDecimal amount,
                           LocalDate paymentDate, String method, String reference, String notes,
                           String receiptNumber, Instant receiptPrintedAt) {}
}
