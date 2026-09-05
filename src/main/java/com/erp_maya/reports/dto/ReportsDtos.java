package com.erp_maya.reports.dto;

import com.erp_maya.dashboard.dto.DashboardDtos.TopProduct;
import com.erp_maya.dashboard.dto.DashboardDtos.TrendPoint;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Respuestas de la reportería. */
public final class ReportsDtos {

    private ReportsDtos() {}

    @Serdeable
    public record BranchRow(Long branchId, String name, long tickets, BigDecimal total) {}

    @Serdeable
    public record PaymentRow(String method, BigDecimal total) {}

    @Serdeable
    public record CategoryMargin(String category, BigDecimal sales, BigDecimal cost,
                                 BigDecimal profit, BigDecimal marginPct) {}

    /** Fila del Libro de Ventas (SAT): gravable + IVA 12% derivados del total. */
    @Serdeable
    public record SalesBookRow(LocalDate date, long tickets, BigDecimal taxable, BigDecimal iva, BigDecimal total) {}

    @Serdeable
    public record SalesReport(BigDecimal totalSales, long totalTickets, BigDecimal avgTicket,
                              List<TrendPoint> trend, List<BranchRow> byBranch, List<PaymentRow> byPayment,
                              List<TopProduct> topProducts, List<CategoryMargin> byCategory,
                              List<SalesBookRow> salesBook) {}
}
