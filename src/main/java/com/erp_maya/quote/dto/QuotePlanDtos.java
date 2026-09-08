package com.erp_maya.quote.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Plan de pagos de la cotización y su estado frente a los cobros reales. */
public final class QuotePlanDtos {

    private QuotePlanDtos() {}

    @Serdeable
    public record TermRequest(Integer sequence,
                              @NotNull @DecimalMin(value = "0.01", message = "El monto de la cuota debe ser mayor a cero") BigDecimal amount,
                              LocalDate dueDate, String notes) {}

    @Serdeable
    public record TermResponse(Long id, Integer sequence, BigDecimal amount, LocalDate dueDate, String notes) {}

    @Serdeable
    public record PaymentRow(Long id, BigDecimal amount, LocalDate paymentDate, String method,
                             String reference, String receiptNumber) {}

    /**
     * Resumen del plan y los cobros de la cotización:
     *   quoteTotal  total operativo con IVA = subtotal + impuesto
     *   planTotal   Σ cuotas del plan
     *   remaining   quoteTotal - planTotal (monto aún no asignado)
     *   balanced    true únicamente cuando planTotal = quoteTotal
     *   collected   Σ cobros reales imputados a la cotización
     *   pending     planTotal - collected (no negativo)
     */
    @Serdeable
    public record Plan(BigDecimal quoteTotal, BigDecimal planTotal, BigDecimal remaining,
                       BigDecimal collected, BigDecimal pending, boolean balanced,
                       List<TermResponse> terms, List<PaymentRow> payments) {}
}
