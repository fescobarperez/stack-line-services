package com.erp_maya.dashboard.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.dashboard.dto.DashboardDtos.BranchSales;
import com.erp_maya.dashboard.dto.DashboardDtos.CategorySales;
import com.erp_maya.dashboard.dto.DashboardDtos.Dashboard;
import com.erp_maya.dashboard.dto.DashboardDtos.Kpis;
import com.erp_maya.dashboard.dto.DashboardDtos.LowStockItem;
import com.erp_maya.dashboard.dto.DashboardDtos.RecentTicket;
import com.erp_maya.dashboard.dto.DashboardDtos.TopProduct;
import com.erp_maya.dashboard.dto.DashboardDtos.TrendPoint;
import com.erp_maya.dashboard.repository.DashboardSaleItemRepository;
import com.erp_maya.dashboard.repository.DashboardSaleRepository;
import com.erp_maya.dashboard.repository.DashboardStockRepository;
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

/** Arma el Dashboard general agregando ventas, líneas de venta y existencias. */
@Singleton
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final DashboardSaleRepository sales;
    private final DashboardSaleItemRepository items;
    private final DashboardStockRepository stock;
    private final TenantContext tenant;

    public DashboardService(DashboardSaleRepository sales, DashboardSaleItemRepository items,
                            DashboardStockRepository stock, TenantContext tenant) {
        this.sales = sales;
        this.items = items;
        this.stock = stock;
        this.tenant = tenant;
    }

    private static BigDecimal bd(Object v) {
        return v != null ? (BigDecimal) v : BigDecimal.ZERO;
    }

    private static long lng(Object v) {
        return v != null ? ((Number) v).longValue() : 0L;
    }

    @Transactional
    public Dashboard build(int days) {
        Long companyId = tenant.getCompanyId();
        LocalDate today = LocalDate.now(ZONE);
        Instant startOfToday = today.atStartOfDay(ZONE).toInstant();
        Instant from = today.minusDays(days - 1L).atStartOfDay(ZONE).toInstant();

        // KPIs de hoy
        List<Object[]> totalsRow = sales.totalsSince(companyId, startOfToday);
        BigDecimal salesToday = totalsRow.isEmpty() ? BigDecimal.ZERO : bd(totalsRow.get(0)[0]);
        long ticketsToday = totalsRow.isEmpty() ? 0 : lng(totalsRow.get(0)[1]);
        BigDecimal avgTicket = ticketsToday > 0
                ? salesToday.divide(BigDecimal.valueOf(ticketsToday), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<LowStockItem> lowStock = stock.lowStock(companyId).stream()
                .map(r -> new LowStockItem((Long) r[0], (String) r[1], (String) r[2], bd(r[3]), bd(r[4])))
                .toList();

        Kpis kpis = new Kpis(salesToday, ticketsToday, avgTicket, lowStock.size());

        return new Dashboard(kpis, salesTrend(companyId, from, days),
                topProducts(companyId, from), categorySales(companyId, from),
                branchSales(companyId, from), lowStock, recentTickets(companyId));
    }

    private List<TrendPoint> salesTrend(Long companyId, Instant from, int days) {
        // Acumula por día (total + número de tickets).
        Map<LocalDate, BigDecimal> totalByDay = new LinkedHashMap<>();
        Map<LocalDate, Long> countByDay = new LinkedHashMap<>();
        for (Object[] r : sales.amountsSince(companyId, from)) {
            LocalDate d = ((Instant) r[0]).atZone(ZONE).toLocalDate();
            totalByDay.merge(d, bd(r[1]), BigDecimal::add);
            countByDay.merge(d, 1L, Long::sum);
        }
        // Serie continua con ceros para días sin ventas.
        List<TrendPoint> trend = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            trend.add(new TrendPoint(d, totalByDay.getOrDefault(d, BigDecimal.ZERO), countByDay.getOrDefault(d, 0L)));
        }
        return trend;
    }

    private List<TopProduct> topProducts(Long companyId, Instant from) {
        return items.topProductsSince(companyId, from, Pageable.from(0, 10)).stream()
                .map(r -> new TopProduct((Long) r[0], (String) r[1], (String) r[2], bd(r[3]), bd(r[4])))
                .toList();
    }

    private List<CategorySales> categorySales(Long companyId, Instant from) {
        return items.categorySalesSince(companyId, from).stream()
                .map(r -> new CategorySales((String) r[0], bd(r[1])))
                .toList();
    }

    private List<BranchSales> branchSales(Long companyId, Instant from) {
        return sales.branchSalesSince(companyId, from).stream()
                .map(r -> new BranchSales((Long) r[0], (String) r[1], bd(r[2])))
                .toList();
    }

    private List<RecentTicket> recentTickets(Long companyId) {
        return sales.recentTickets(companyId, Pageable.from(0, 8)).stream()
                .map(r -> new RecentTicket((String) r[0], (Instant) r[1], (String) r[2], (String) r[3], bd(r[4])))
                .toList();
    }
}
