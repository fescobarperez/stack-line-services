package com.erp_maya.pos.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    /** Una venta del turno, lo justo para listarla en el detalle. */
    @Serdeable
    public record ShiftSale(Long id, String docNumber, Instant createdAt,
                            String paymentMethod, BigDecimal total, String status) {}

    /**
     * Detalle de un turno para el panel lateral.
     *
     * `otherTotal` existe porque salesCash y salesCard no suman salesTotal: una
     * venta por transferencia, cheque o depósito entra al total y a ninguno de
     * los dos. Sin ese renglón las cifras no cuadran a la vista.
     *
     * `expectedCash` es lo que debería haber en la gaveta al cerrar: fondo de
     * apertura más efectivo vendido menos devoluciones.
     */
    @Serdeable
    public record ShiftDetail(Response register, long ticketCount,
                              BigDecimal otherTotal, BigDecimal expectedCash,
                              BigDecimal averageTicket, List<ShiftSale> lastSales) {}
}
