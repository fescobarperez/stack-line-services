package com.erp_maya.purchasing.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {}

    @Serdeable
    public record ItemRequest(@NotNull Long productId, @NotNull BigDecimal qtyOrdered,
                              @NotNull BigDecimal unitCost) {}

    @Serdeable
    public record Request(String docNumber, Long supplierId, Long branchId, Long projectId,
                          LocalDate orderDate,
                          String notes, @NotEmpty @Valid List<ItemRequest> items) {}

    @Serdeable
    public record ItemResponse(Long id, Long productId, String productName, BigDecimal qtyOrdered,
                               BigDecimal qtyReceived, BigDecimal unitCost) {}

    @Serdeable
    public record Response(Long id, String docNumber, Long supplierId, String supplierName,
                           Long branchId, String branchName, Long projectId,
                           LocalDate orderDate, BigDecimal total,
                           String status, String notes, List<ItemResponse> items) {}

    /** Recepción: cantidades a recibir por renglón. Vacío = recibir todo lo pendiente. */
    @Serdeable
    public record ReceiptItem(@NotNull Long itemId, @NotNull BigDecimal quantity) {}

    @Serdeable
    public record ReceiveRequest(List<ReceiptItem> items) {}
}
