package com.erp_maya.payroll.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public final class PayrollDtos {

    private PayrollDtos() {}

    @Serdeable
    public record PeriodRequest(@NotBlank String periodCode, String name, Integer month, Integer year) {}

    @Serdeable
    public record EntryResponse(Long id, Long employeeId, String employeeName, BigDecimal baseSalary,
                                BigDecimal bonuses, BigDecimal igssDeduction, BigDecimal isrDeduction,
                                BigDecimal otherDeductions, BigDecimal netPay) {}

    @Serdeable
    public record PeriodResponse(Long id, String periodCode, String name, Integer month, Integer year,
                                 String status, BigDecimal total, Integer employeeCount,
                                 List<EntryResponse> entries) {}

    // ── Reporte: Planilla de IGSS ─────────────────────────────────────────
    @Serdeable
    public record IgssLine(String employeeCode, String name, String dpi, String nit,
                           BigDecimal baseSalary, BigDecimal igssLaboral, BigDecimal igssPatronal) {}

    @Serdeable
    public record IgssReport(Long periodId, String periodName, Integer employeeCount,
                             BigDecimal totalBase, BigDecimal totalLaboral, BigDecimal totalPatronal,
                             BigDecimal totalIgss, List<IgssLine> lines) {}

    // ── Reporte: Formulario SAT — retención de ISR ────────────────────────
    @Serdeable
    public record IsrLine(String employeeCode, String name, String nit, BigDecimal annualSalary,
                          BigDecimal taxableIncome, BigDecimal isrAnnual, BigDecimal isrMonthly) {}

    @Serdeable
    public record IsrReport(Long periodId, String periodName, Integer employeeCount,
                            BigDecimal totalIsrMonthly, BigDecimal totalIsrAnnual, List<IsrLine> lines) {}
}
