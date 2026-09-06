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
    /**
     * `productId` o `concept`: uno de los dos. Sin producto es un renglón de
     * servicio —anticipo, avance de proyecto, mano de obra— que no toca bodega.
     */
    public record ItemRequest(Long productId, String concept, @NotNull BigDecimal quantity,
                              @NotNull BigDecimal unitPrice, BigDecimal discount,
                              String discountSource, Long promotionId) {}

    @Serdeable
    /**
     * `cashRegisterId` es opcional solo para una venta a crédito: esa no mueve
     * dinero, así que no pertenece a ningún arqueo. Una venta de contado sin
     * turno se rechaza — sería la puerta de atrás al control de cajas.
     */
    public record Request(String docNumber, String docType, String series, Long relatedSaleId,
                          String reason, Long clientId, Long branchId, Long userId,
                          Long cashRegisterId, String paymentMethod, String status,
                          Boolean credit, BigDecimal discountTotal, Long authorizationId,
                          Long projectId, @NotEmpty @Valid List<ItemRequest> items) {}

    @Serdeable
    public record ItemResponse(Long id, Long productId, String productName, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal) {}

    @Serdeable
    public record Response(Long id, String docNumber, String docType, String series,
                           Long relatedSaleId, String reason, Long clientId, String clientName,
                           Long branchId, String branchName, Long userId, Long cashRegisterId,
                           Instant saleDate, String paymentMethod, BigDecimal subtotal, BigDecimal tax,
                           BigDecimal taxRate, BigDecimal total, BigDecimal signedTotal,
                           boolean credit, Long projectId,
                           String status, List<ItemResponse> items) {}
}
