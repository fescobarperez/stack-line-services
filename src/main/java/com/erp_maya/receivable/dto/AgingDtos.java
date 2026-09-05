package com.erp_maya.receivable.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Respuestas de la antigüedad de saldos (CxC aging). */
public final class AgingDtos {

    private AgingDtos() {}

    /** Documento por cobrar (una venta a crédito) con su saldo y antigüedad. */
    @Serdeable
    public record InvoiceRow(Long saleId, String docNumber, Long clientId, String clientName,
                             LocalDate saleDate, LocalDate dueDate, BigDecimal amount, BigDecimal paid,
                             BigDecimal outstanding, long daysOverdue, String bucket) {}

    @Serdeable
    public record BucketRow(String bucket, int count, BigDecimal total) {}

    @Serdeable
    public record ClientRow(Long clientId, String clientName, BigDecimal total, Map<String, BigDecimal> buckets) {}

    @Serdeable
    public record Aging(BigDecimal totalReceivable, BigDecimal overdue, int openCount, int criticalCount,
                        List<BucketRow> summary, List<InvoiceRow> invoices, List<ClientRow> byClient) {}
}
