package com.erp_maya.pos.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SaleDtos {

    private SaleDtos() {}

    @Serdeable
    public record ItemRequest(@NotNull Long productId, @NotNull BigDecimal quantity,
                              @NotNull BigDecimal unitPrice, BigDecimal discount,
                              String discountSource, Long promotionId) {}

    @Serdeable
    public record Request(String docNumber, Long clientId, @NotNull Long branchId, Long userId,
                          @NotNull Long cashRegisterId, String paymentMethod, String status,
                          BigDecimal discountTotal,
                          @NotEmpty @Valid List<ItemRequest> items) {}

    @Serdeable
    public record ItemResponse(Long id, Long productId, String productName, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal) {}

    @Serdeable
    public record Response(Long id, String docNumber, Long clientId, String clientName,
                           Long branchId, String branchName, Long userId, Long cashRegisterId,
                           Instant saleDate, String paymentMethod, BigDecimal subtotal, BigDecimal tax,
                           BigDecimal total, String status, List<ItemResponse> items) {}
}
