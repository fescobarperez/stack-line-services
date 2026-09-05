package com.erp_maya.accounting.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;

/** Respuestas de los reportes contables derivados (balance de comprobación y estados financieros). */
public final class AccountingReportDtos {

    private AccountingReportDtos() {}

    /** Fila del balance de comprobación: débitos, créditos y saldo por cuenta. */
    @Serdeable
    public record TrialBalanceRow(Long accountId, String code, String name, String normalBalance,
                                  BigDecimal debit, BigDecimal credit, BigDecimal balance) {}

    @Serdeable
    public record TrialBalance(Long periodId, List<TrialBalanceRow> rows,
                               BigDecimal totalDebit, BigDecimal totalCredit) {}

    /** Línea de un estado financiero (una cuenta de detalle con su saldo natural). */
    @Serdeable
    public record StatementLine(String code, String name, BigDecimal amount) {}

    @Serdeable
    public record BalanceSheet(Long periodId,
                               List<StatementLine> assets, List<StatementLine> liabilities, List<StatementLine> equity,
                               BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal totalEquity,
                               BigDecimal totalLiabilitiesAndEquity) {}

    @Serdeable
    public record IncomeStatement(Long periodId,
                                  List<StatementLine> income, List<StatementLine> expenses,
                                  BigDecimal totalIncome, BigDecimal totalExpenses, BigDecimal netIncome) {}
}
