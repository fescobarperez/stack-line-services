package com.erp_maya.accounting.controller;

import com.erp_maya.accounting.dto.AccountingReportDtos.BalanceSheet;
import com.erp_maya.accounting.dto.AccountingReportDtos.IncomeStatement;
import com.erp_maya.accounting.dto.AccountingReportDtos.TrialBalance;
import com.erp_maya.accounting.dto.LedgerDtos;
import com.erp_maya.accounting.service.AccountingReportService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;

/** Reportes contables derivados: balance de comprobación y estados financieros. */
@Controller("/api/accounting")
public class AccountingReportController {

    private final AccountingReportService service;

    public AccountingReportController(AccountingReportService service) {
        this.service = service;
    }

    @Get("/trial-balance")
    public TrialBalance trialBalance(@Nullable @QueryValue Long periodId) {
        return service.trialBalance(periodId);
    }

    @Get("/balance-sheet")
    public BalanceSheet balanceSheet(@Nullable @QueryValue Long periodId) {
        return service.balanceSheet(periodId);
    }

    @Get("/income-statement")
    public IncomeStatement incomeStatement(@Nullable @QueryValue Long periodId) {
        return service.incomeStatement(periodId);
    }

    @Get("/ledger/trial-balance")
    public LedgerDtos.TrialBalance ledgerTrialBalance(@Nullable @QueryValue Long periodId) {
        return service.ledgerTrialBalance(periodId);
    }

    @Get("/ledger/account/{accountId}")
    public LedgerDtos.AccountLedger accountLedger(@PathVariable Long accountId,
                                                  @Nullable @QueryValue Long periodId) {
        return service.accountLedger(accountId, periodId);
    }
}
