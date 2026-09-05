package com.erp_maya.returns.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CreditNoteDtos {

    private CreditNoteDtos() {}

    /** Renglón. productId opcional: si no viene, se usa itemName (texto libre). */
    @Serdeable
    public record ItemRequest(Long productId, String itemName, @NotNull BigDecimal quantity, BigDecimal unitPrice) {}

    @Serdeable
    public record Request(String docNumber, String noteType, String ticketRef, Long saleId,
                          Long clientId, String clientName, String clientNit, String cashier, String branchName,
                          LocalDate returnDate, String reason, String refundMethod,
                          @NotEmpty @Valid List<ItemRequest> items) {}

    @Serdeable
    public record ItemResponse(Long id, Long productId, String productName, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal) {}

    @Serdeable
    public record Response(Long id, String docNumber, String noteType, String ticketRef, Long saleId,
                           Long clientId, String clientName, String clientNit, String cashier, String branchName,
                           LocalDate returnDate, String reason, String refundMethod, BigDecimal total,
                           String status, String felStatus, String felUuid, String felError,
                           List<ItemResponse> items) {}

    /** Reintento/actualización del estado FEL de la nota de crédito. */
    @Serdeable
    public record FelRequest(String felStatus, String felUuid, String felError) {}
}
