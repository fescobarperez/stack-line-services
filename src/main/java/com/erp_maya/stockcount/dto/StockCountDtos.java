package com.erp_maya.stockcount.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StockCountDtos {

    private StockCountDtos() {}

    @Serdeable
    public record ItemRequest(@NotNull Long productId, @NotNull BigDecimal systemQty,
                              BigDecimal countedQty, String lineNotes) {}

    /** Crear sesión. Si items viene vacío, se toma foto del stock actual de la sucursal. */
    @Serdeable
    public record Request(String docNumber, @NotNull Long branchId, LocalDate countDate,
                          String responsible, String category, String categoryLabel, String notes,
                          @Valid List<ItemRequest> items) {}

    @Serdeable
    public record ItemResponse(Long id, Long productId, String productName, String sku, String unit,
                               String category, BigDecimal unitCost, BigDecimal systemQty,
                               BigDecimal countedQty, BigDecimal difference, String lineNotes) {}

    @Serdeable
    public record Response(Long id, String docNumber, Long branchId, String branchName, LocalDate countDate,
                           String responsible, String category, String categoryLabel, String status, String notes,
                           Integer discrepancies, Integer adjustedQty, List<ItemResponse> items) {}

    /** Guardar avance del conteo: cantidades contadas + notas por renglón, y estado opcional. */
    @Serdeable
    public record CountLine(@NotNull Long itemId, BigDecimal countedQty, String lineNotes) {}

    @Serdeable
    public record CountUpdate(List<CountLine> lines, String status) {}
}
