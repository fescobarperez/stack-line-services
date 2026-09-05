package com.erp_maya.payable.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PayableDtos {

    private PayableDtos() {}

    @Serdeable
    public record InvoiceRequest(String docNumber, @NotNull Long supplierId, Long purchaseOrderId,
                                 LocalDate invoiceDate, LocalDate dueDate, @NotNull BigDecimal amount) {}

    @Serdeable
    public record InvoiceResponse(Long id, String docNumber, Long supplierId, String supplierName,
                                  Long purchaseOrderId, LocalDate invoiceDate, LocalDate dueDate,
                                  BigDecimal amount, BigDecimal paidAmount, String status) {}

    @Serdeable
    public record PaymentRequest(@NotNull Long supplierId, Long purchaseInvoiceId, @NotNull BigDecimal amount,
                                 LocalDate paymentDate, String method, String reference, String notes) {}

    @Serdeable
    public record PaymentResponse(Long id, Long supplierId, String supplierName, Long purchaseInvoiceId,
                                  BigDecimal amount, LocalDate paymentDate, String method,
                                  String reference, String notes) {}
}
