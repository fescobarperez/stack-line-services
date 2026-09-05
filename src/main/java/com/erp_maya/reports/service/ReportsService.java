package com.erp_maya.reports.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.dashboard.dto.DashboardDtos.TopProduct;
import com.erp_maya.dashboard.dto.DashboardDtos.TrendPoint;
import com.erp_maya.dashboard.repository.DashboardSaleItemRepository;
import com.erp_maya.dashboard.repository.DashboardSaleRepository;
import com.erp_maya.reports.dto.ReportsDtos.BranchRow;
import com.erp_maya.reports.dto.ReportsDtos.CategoryMargin;
import com.erp_maya.reports.dto.ReportsDtos.PaymentRow;
import com.erp_maya.reports.dto.ReportsDtos.SalesBookRow;
import com.erp_maya.reports.dto.ReportsDtos.SalesReport;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reportería de ventas: totales, series, sucursal, método de pago, margen y libro de ventas. */
@Singleton
public class ReportsService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final BigDecimal IVA_FACTOR = new BigDecimal("1.12");

    private final DashboardSaleRepository sales;
    private final DashboardSaleItemRepository items;
    private final TenantContext tenant;

    public ReportsService(DashboardSaleRepository sales, DashboardSaleItemRepository items, TenantContext tenant) {
        this.sales = sales;
        this.items = items;
        this.tenant = tenant;
    }

    private static BigDecimal bd(Object v) {
        return v != null ? (BigDecimal) v : BigDecimal.ZERO;
    }

    private static long lng(Object v) {
        return v != null ? ((Number) v).longValue() : 0L;
    }

    @Transactional
    public SalesReport salesReport(int days) {
        Long companyId = tenant.getCompanyId();
        LocalDate today = LocalDate.now(ZONE);
        Instant from = today.minusDays(days - 1L).atStartOfDay(ZONE).toInstant();

        // Acumula total y tickets por día (para serie y libro de ventas).
        Map<LocalDate, BigDecimal> totalByDay = new LinkedHashMap<>();
        Map<LocalDate, Long> countByDay = new LinkedHashMap<>();
        for (Object[] r : sales.amountsSince(companyId, from)) {
            LocalDate d = ((Instant) r[0]).atZone(ZONE).toLocalDate();
            totalByDay.merge(d, bd(r[1]), BigDecimal::add);
            countByDay.merge(d, 1L, Long::sum);
        }

        List<TrendPoint> trend = new ArrayList<>();
        List<SalesBookRow> salesBook = new ArrayList<>();
        BigDecimal totalSales = BigDecimal.ZERO;
        long totalTickets = 0;
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            BigDecimal total = totalByDay.getOrDefault(d, BigDecimal.ZERO);
            long tickets = countByDay.getOrDefault(d, 0L);
            trend.add(new TrendPoint(d, total, tickets));
            if (tickets > 0) {
                BigDecimal taxable = total.divide(IVA_FACTOR, 2, RoundingMode.HALF_UP);
                salesBook.add(new SalesBookRow(d, tickets, taxable, total.subtract(taxable), total));
            }
            totalSales = totalSales.add(total);
            totalTickets += tickets;
        }
        BigDecimal avgTicket = totalTickets > 0
                ? totalSales.divide(BigDecimal.valueOf(totalTickets), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<BranchRow> byBranch = sales.branchSalesWithCountSince(companyId, from).stream()
                .map(r -> new BranchRow((Long) r[0], (String) r[1], lng(r[2]), bd(r[3])))
                .toList();

        List<PaymentRow> byPayment = sales.paymentBreakdownSince(companyId, from).stream()
                .map(r -> new PaymentRow((String) r[0], bd(r[1])))
                .toList();

        List<TopProduct> topProducts = items.topProductsSince(companyId, from, Pageable.from(0, 10)).stream()
                .map(r -> new TopProduct((Long) r[0], (String) r[1], (String) r[2], bd(r[3]), bd(r[4])))
                .toList();

        List<CategoryMargin> byCategory = items.categoryMarginSince(companyId, from).stream()
                .map(r -> {
                    BigDecimal s = bd(r[1]);
                    BigDecimal cost = bd(r[2]);
                    BigDecimal profit = s.subtract(cost);
                    BigDecimal marginPct = s.signum() != 0
                            ? profit.multiply(BigDecimal.valueOf(100)).divide(s, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new CategoryMargin((String) r[0], s, cost, profit, marginPct);
                })
                .toList();

        return new SalesReport(totalSales, totalTickets, avgTicket, trend, byBranch, byPayment,
                topProducts, byCategory, salesBook);
    }
}
