package com.erp_maya.receivable.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.receivable.dto.AgingDtos.Aging;
import com.erp_maya.receivable.dto.AgingDtos.BucketRow;
import com.erp_maya.receivable.dto.AgingDtos.ClientRow;
import com.erp_maya.receivable.dto.AgingDtos.InvoiceRow;
import com.erp_maya.receivable.repository.CreditSaleRepository;
import com.erp_maya.receivable.repository.PaymentRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Antigüedad de saldos (CxC aging): trata cada venta a crédito como un documento por
 * cobrar. Vencimiento = fecha de venta + términos del cliente; saldo = total − abonos.
 */
@Singleton
public class ReceivableAgingService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final BigDecimal EPS = new BigDecimal("0.005");
    private static final List<String> BUCKETS = List.of("current", "1-30", "31-60", "61-90", "90+");

    private final CreditSaleRepository creditSales;
    private final PaymentRepository payments;
    private final TenantContext tenant;

    public ReceivableAgingService(CreditSaleRepository creditSales, PaymentRepository payments, TenantContext tenant) {
        this.creditSales = creditSales;
        this.payments = payments;
        this.tenant = tenant;
    }

    private static BigDecimal bd(Object v) {
        return v != null ? (BigDecimal) v : BigDecimal.ZERO;
    }

    private static String bucketOf(long daysOverdue) {
        if (daysOverdue <= 0) return "current";
        if (daysOverdue <= 30) return "1-30";
        if (daysOverdue <= 60) return "31-60";
        if (daysOverdue <= 90) return "61-90";
        return "90+";
    }

    @Transactional
    public Aging aging() {
        Long companyId = tenant.getCompanyId();
        LocalDate today = LocalDate.now(ZONE);

        Map<Long, BigDecimal> paidBySale = new HashMap<>();
        for (Object[] r : payments.paidBySale(companyId)) {
            paidBySale.put((Long) r[0], bd(r[1]));
        }

        List<InvoiceRow> invoices = new ArrayList<>();
        BigDecimal totalReceivable = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        int criticalCount = 0;

        // Resumen por bucket y por cliente.
        Map<String, int[]> bucketCount = new LinkedHashMap<>();
        Map<String, BigDecimal> bucketTotal = new LinkedHashMap<>();
        BUCKETS.forEach(b -> { bucketCount.put(b, new int[]{0}); bucketTotal.put(b, BigDecimal.ZERO); });
        Map<Long, ClientAcc> byClient = new LinkedHashMap<>();

        for (Object[] r : creditSales.creditSales(companyId)) {
            Long saleId = (Long) r[0];
            String docNumber = (String) r[1];
            Long clientId = (Long) r[2];
            String clientName = (String) r[3];
            LocalDate saleDate = ((Instant) r[4]).atZone(ZONE).toLocalDate();
            BigDecimal amount = bd(r[5]);
            int terms = r[6] != null ? ((Number) r[6]).intValue() : 0;

            BigDecimal paid = paidBySale.getOrDefault(saleId, BigDecimal.ZERO);
            BigDecimal outstanding = amount.subtract(paid);
            if (outstanding.compareTo(EPS) <= 0) {
                continue; // saldado
            }

            LocalDate dueDate = saleDate.plusDays(terms);
            long daysOverdue = ChronoUnit.DAYS.between(dueDate, today);
            String bucket = bucketOf(daysOverdue);

            invoices.add(new InvoiceRow(saleId, docNumber, clientId, clientName, saleDate, dueDate,
                    amount, paid, outstanding, daysOverdue, bucket));

            totalReceivable = totalReceivable.add(outstanding);
            if (daysOverdue > 0) overdue = overdue.add(outstanding);
            if (daysOverdue > 60) criticalCount++;

            bucketCount.get(bucket)[0]++;
            bucketTotal.merge(bucket, outstanding, BigDecimal::add);

            ClientAcc acc = byClient.computeIfAbsent(clientId, k -> new ClientAcc(clientName));
            acc.total = acc.total.add(outstanding);
            acc.buckets.merge(bucket, outstanding, BigDecimal::add);
        }

        List<BucketRow> summary = new ArrayList<>();
        for (String b : BUCKETS) {
            summary.add(new BucketRow(b, bucketCount.get(b)[0], bucketTotal.get(b)));
        }

        List<ClientRow> clientRows = byClient.entrySet().stream()
                .map(e -> new ClientRow(e.getKey(), e.getValue().name, e.getValue().total, e.getValue().buckets))
                .sorted((a, b) -> b.total().compareTo(a.total()))
                .toList();

        return new Aging(totalReceivable, overdue, invoices.size(), criticalCount, summary, invoices, clientRows);
    }

    private static final class ClientAcc {
        final String name;
        BigDecimal total = BigDecimal.ZERO;
        final Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        ClientAcc(String name) { this.name = name; }
    }
}
