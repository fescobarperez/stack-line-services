package com.erp_maya.transfer.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TransferDtos {

    private TransferDtos() {}

    @Serdeable
    public record ItemRequest(@NotNull Long productId, @NotNull BigDecimal quantity) {}

    @Serdeable
    public record Request(String docNumber, @NotNull Long fromBranchId, @NotNull Long toBranchId,
                          String transporter, LocalDate transferDate,
                          @NotEmpty @Valid List<ItemRequest> items) {}

    @Serdeable
    public record ItemResponse(Long id, Long productId, String productName,
                               BigDecimal quantity, BigDecimal qtyReceived) {}

    @Serdeable
    public record Response(Long id, String docNumber, Long fromBranchId, String fromBranchName,
                           Long toBranchId, String toBranchName, String transporter,
                           LocalDate transferDate, String status, List<ItemResponse> items) {}
}
