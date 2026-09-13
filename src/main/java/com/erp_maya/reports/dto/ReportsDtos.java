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

    /**
     * Un proyecto en el ranking de rentabilidad.
     *
     * `margin` es el firme —contratado menos lo realmente ejecutado— y
     * `projectedMargin` descuenta además el material pendiente, los cargos de
     * cotizaciones no aprobadas y las órdenes de compra vivas. En un proyecto
     * cerrado los dos coinciden; en uno abierto la diferencia es justo lo que
     * falta por gastar.
     */
    @Serdeable
    public record ProjectMarginRow(Long projectId, String code, String name,
                                   String clientName, String status,
                                   BigDecimal contracted, BigDecimal executed,
                                   BigDecimal margin, BigDecimal marginPct,
                                   BigDecimal projectedMargin,
                                   LocalDate startDate, LocalDate endDate) {}

    /**
     * Top K de proyectos por rentabilidad, en las dos direcciones.
     *
     * `evaluated` es cuántos proyectos entraron al ranking tras el filtro de
     * estado: sin ese dato, un top de dos filas parece un error de la consulta
     * en vez de que solo haya dos proyectos que cumplan.
     */
    @Serdeable
    public record ProjectProfitability(String orderBy, String statusFilter, int evaluated,
                                       BigDecimal totalMargin, BigDecimal avgMarginPct,
                                       List<ProjectMarginRow> best,
                                       List<ProjectMarginRow> worst) {}

    @Serdeable
    public record SalesReport(BigDecimal totalSales, long totalTickets, BigDecimal avgTicket,
                              List<TrendPoint> trend, List<BranchRow> byBranch, List<PaymentRow> byPayment,
                              List<TopProduct> topProducts, List<CategoryMargin> byCategory,
                              List<SalesBookRow> salesBook) {}
}
