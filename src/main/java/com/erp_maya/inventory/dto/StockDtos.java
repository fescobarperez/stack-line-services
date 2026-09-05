package com.erp_maya.inventory.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class StockDtos {

    private StockDtos() {}

    /** Existencia de un producto en una sucursal/lote. */
    @Serdeable
    public record StockRow(Long id, Long productId, String sku, String productName,
                           Long branchId, String branchName, BigDecimal quantity,
                           String batch, LocalDate expiry) {}

    /** Renglón del kardex. */
    @Serdeable
    public record Movement(Long id, Long productId, String productName, Long branchId, String branchName,
                           String movementType, BigDecimal quantity, String refType, String refId,
                           Long userId, Instant createdAt) {}

    /** Ajuste manual de inventario (delta con signo). */
    @Serdeable
    public record AdjustmentRequest(@NotNull Long productId, @NotNull Long branchId,
                                    @NotNull BigDecimal quantity, String batch, String note) {}
}
