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
    /**
     * `unapplied` son los abonos a cuenta del cliente, sin documento asignado.
     * `total` ya los descuenta, para que cuadre con v_client_balance; los
     * buckets no, porque un anticipo no tiene antigüedad que mostrar.
     */
    public record ClientRow(Long clientId, String clientName, BigDecimal total,
                            BigDecimal unapplied, Map<String, BigDecimal> buckets) {}

    @Serdeable
    /**
     * `totalReceivable` es la suma de documentos abiertos; `unapplied`, los
     * abonos a cuenta; `netReceivable`, la diferencia — que es lo que de verdad
     * se debe y lo que coincide con la suma de v_client_balance.
     */
    public record Aging(BigDecimal totalReceivable, BigDecimal unapplied, BigDecimal netReceivable,
                        BigDecimal overdue, int openCount, int criticalCount,
                        List<BucketRow> summary, List<InvoiceRow> invoices, List<ClientRow> byClient) {}
}
