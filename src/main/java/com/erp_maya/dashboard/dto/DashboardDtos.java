package com.erp_maya.dashboard.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Respuesta agregada del Dashboard general (métricas + series + tablas). */
public final class DashboardDtos {

    private DashboardDtos() {}

    @Serdeable
    public record Kpis(BigDecimal salesToday, long ticketsToday, BigDecimal avgTicket, long lowStockCount) {}

    @Serdeable
    public record TrendPoint(LocalDate date, BigDecimal total, long tickets) {}

    @Serdeable
    public record TopProduct(Long productId, String sku, String name, BigDecimal quantity, BigDecimal total) {}

    @Serdeable
    public record CategorySales(String category, BigDecimal total) {}

    @Serdeable
    public record BranchSales(Long branchId, String name, BigDecimal total) {}

    @Serdeable
    public record LowStockItem(Long productId, String sku, String name, BigDecimal stock, BigDecimal minStock) {}

    @Serdeable
    public record RecentTicket(String docNumber, Instant saleDate, String branch, String paymentMethod, BigDecimal total) {}

    @Serdeable
    public record Dashboard(Kpis kpis, List<TrendPoint> salesTrend, List<TopProduct> topProducts,
                            List<CategorySales> salesByCategory, List<BranchSales> branchSales,
                            List<LowStockItem> lowStock, List<RecentTicket> recentTickets) {}
}
