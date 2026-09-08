package com.erp_maya.project.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProjectDtos {

    @Serdeable
    public record Request(@NotBlank String name, @NotNull Long clientId, String code,
                          Long costCenterId, BigDecimal contractedAmount, String currency,
                          LocalDate startDate, LocalDate endDate, String notes, String status) {}

    /** Conversión desde cotización: el monto sale de la propia cotización. */
    @Serdeable
    public record FromQuoteRequest(String name, String code, Long costCenterId,
                                   LocalDate startDate, LocalDate endDate, String notes) {}

    /**
     * Consumo de materia prima en el proyecto. Descuenta existencias y carga el
     * costo al promedio de bodega: imputar la COMPRA en vez del consumo haría
     * que el primer proyecto cargara con el lote entero.
     */
    @Serdeable
    public record ConsumeRequest(@NotNull Long productId, @NotNull Long branchId,
                                 @NotNull BigDecimal quantity, String batch, String notes,
                                 Long authorizationId) {}

    @Serdeable
    public record CostRequest(@NotNull String source, @NotNull BigDecimal amount,
                              String description, Long refId, LocalDate costDate,
                              Long authorizationId) {}

    /** Cobro imputado al proyecto: adelanto, avance o liquidación. */
    @Serdeable
    public record PaymentResponse(Long id, BigDecimal amount, LocalDate paymentDate,
                                  String method, String reference, String receiptNumber,
                                  java.time.Instant receiptPrintedAt, Long quoteId) {}

    @Serdeable
    public record CostResponse(Long id, String source, Long refId, String description,
                               BigDecimal amount, LocalDate costDate) {}

    /** Un cargo de la cotización, para el desglose de gastos en el proyecto. */
    @Serdeable
    public record QuoteChargeRef(String category, String description, String calcType,
                                 BigDecimal computedAmount) {}

    @Serdeable
    public record QuoteRef(Long quoteId, String docNumber, String status,
                           BigDecimal operatingCost, BigDecimal profitAmount,
                           BigDecimal subtotal, BigDecimal taxRate, BigDecimal tax, BigDecimal total,
                           Boolean included,
                           BigDecimal materialsCost, List<QuoteChargeRef> charges,
                           BigDecimal expenseTotal) {}

    /**
     * Las cuatro cifras del seguimiento. Con una sola de gasto no se ve venir
     * un sobrecosto: `committed` es lo que ya está pedido y aún no facturado.
     */
    @Serdeable
    public record Response(Long id, String code, String name,
                           Long quoteId, String quoteNumber,
                           Long clientId, String clientName,
                           Long costCenterId, String currency, String status,
                           LocalDate startDate, LocalDate endDate, String notes,
                           BigDecimal contracted,
                           BigDecimal executed,
                           BigDecimal committed,
                           BigDecimal invoiced,
                           BigDecimal collected,
                           BigDecimal margin,
                           BigDecimal projectedMargin,
                           BigDecimal marginPct,
                           BigDecimal pendingToInvoice,
                           BigDecimal pendingToCollect,
                           /** Costo ya incurrido que todavía no se le facturó a nadie. */
                           BigDecimal executedNotInvoiced,
                           String closeNote,
                           List<CostResponse> costs,
                           List<PaymentResponse> payments,
                           BigDecimal materialsCost,
                           /** Costo de materiales en cotizaciones aprobadas (firme). */
                           BigDecimal materialsCostApproved,
                           /** Costo de materiales en cotizaciones no aprobadas (tentativo). */
                           BigDecimal materialsCostTentative,
                           /** Cotizaciones asociadas (para la columna/agregación por cotización). */
                           List<QuoteRef> quotes,
                           /** Conteo de cotizaciones asociadas (barato, disponible también en la lista). */
                           Integer quoteCount) {}
}
