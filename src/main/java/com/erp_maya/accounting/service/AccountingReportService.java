package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.Account;
import com.erp_maya.accounting.domain.AccountingPeriod;
import com.erp_maya.accounting.dto.AccountAggregate;
import com.erp_maya.accounting.dto.AccountingReportDtos.BalanceSheet;
import com.erp_maya.accounting.dto.AccountingReportDtos.IncomeStatement;
import com.erp_maya.accounting.dto.AccountingReportDtos.StatementLine;
import com.erp_maya.accounting.dto.AccountingReportDtos.TrialBalance;
import com.erp_maya.accounting.dto.AccountingReportDtos.TrialBalanceRow;
import com.erp_maya.accounting.dto.LedgerDtos;
import com.erp_maya.accounting.dto.LedgerLineRow;
import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.accounting.repository.AccountingPeriodRepository;
import com.erp_maya.accounting.repository.JournalEntryLineRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reportes contables derivados de las pólizas (journal_entry_lines): balance de
 * comprobación y estados financieros. Todo se calcula sobre datos reales; el saldo
 * "natural" de cada cuenta respeta su naturaleza (deudora/acreedora).
 */
@Singleton
public class AccountingReportService {

    private final JournalEntryLineRepository lines;
    private final AccountingPeriodRepository periods;
    private final AccountRepository accounts;
    private final TenantContext tenant;

    public AccountingReportService(JournalEntryLineRepository lines,
                                   AccountingPeriodRepository periods,
                                   AccountRepository accounts,
                                   TenantContext tenant) {
        this.lines = lines;
        this.periods = periods;
        this.accounts = accounts;
        this.tenant = tenant;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // Saldo natural: cuenta acreedora → crédito − débito; deudora → débito − crédito.
    private static BigDecimal naturalBalance(AccountAggregate a) {
        BigDecimal debit = nz(a.debit());
        BigDecimal credit = nz(a.credit());
        return "credit".equals(a.normalBalance()) ? credit.subtract(debit) : debit.subtract(credit);
    }

    @Transactional
    public TrialBalance trialBalance(Long periodId) {
        List<AccountAggregate> aggs = lines.aggregateByAccount(tenant.getCompanyId(), periodId);
        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (AccountAggregate a : aggs) {
            BigDecimal debit = nz(a.debit());
            BigDecimal credit = nz(a.credit());
            rows.add(new TrialBalanceRow(a.accountId(), a.code(), a.name(), a.normalBalance(),
                    debit, credit, naturalBalance(a)));
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        return new TrialBalance(periodId, rows, totalDebit, totalCredit);
    }

    @Transactional
    public BalanceSheet balanceSheet(Long periodId) {
        List<AccountAggregate> aggs = lines.aggregateByAccount(tenant.getCompanyId(), periodId);
        List<StatementLine> assets = new ArrayList<>();
        List<StatementLine> liabilities = new ArrayList<>();
        List<StatementLine> equity = new ArrayList<>();
        for (AccountAggregate a : aggs) {
            BigDecimal amount = naturalBalance(a);
            switch (firstDigit(a.code())) {
                case '1' -> assets.add(new StatementLine(a.code(), a.name(), amount));
                case '2' -> liabilities.add(new StatementLine(a.code(), a.name(), amount));
                case '3' -> equity.add(new StatementLine(a.code(), a.name(), amount));
                default -> { /* 4/5 no van en el balance general */ }
            }
        }
        BigDecimal totalAssets = sum(assets);
        BigDecimal totalLiab = sum(liabilities);
        BigDecimal totalEquity = sum(equity);
        return new BalanceSheet(periodId, assets, liabilities, equity,
                totalAssets, totalLiab, totalEquity, totalLiab.add(totalEquity));
    }

    @Transactional
    public IncomeStatement incomeStatement(Long periodId) {
        List<AccountAggregate> aggs = lines.aggregateByAccount(tenant.getCompanyId(), periodId);
        List<StatementLine> income = new ArrayList<>();
        List<StatementLine> expenses = new ArrayList<>();
        for (AccountAggregate a : aggs) {
            BigDecimal amount = naturalBalance(a);
            switch (firstDigit(a.code())) {
                case '4' -> income.add(new StatementLine(a.code(), a.name(), amount));
                case '5' -> expenses.add(new StatementLine(a.code(), a.name(), amount));
                default -> { /* 1/2/3 no van en el estado de resultados */ }
            }
        }
        BigDecimal totalIncome = sum(income);
        BigDecimal totalExpenses = sum(expenses);
        return new IncomeStatement(periodId, income, expenses,
                totalIncome, totalExpenses, totalIncome.subtract(totalExpenses));
    }

    // ── Mayor general ─────────────────────────────────────────────────────

    /** Balance de comprobación con apertura + movimientos del período + saldo final. */
    @Transactional
    public LedgerDtos.TrialBalance ledgerTrialBalance(Long periodId) {
        Long companyId = tenant.getCompanyId();
        List<AccountAggregate> periodAggs = lines.aggregateByAccount(companyId, periodId);
        List<AccountAggregate> beforeAggs = List.of();
        if (periodId != null) {
            AccountingPeriod p = periods.findByIdAndCompanyId(periodId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Período " + periodId + " no encontrado"));
            beforeAggs = lines.aggregateByAccountBeforeDate(companyId, p.getStartDate());
        }
        Map<Long, AccountAggregate> beforeByAcct = new LinkedHashMap<>();
        beforeAggs.forEach(a -> beforeByAcct.put(a.accountId(), a));
        Map<Long, AccountAggregate> periodByAcct = new LinkedHashMap<>();
        periodAggs.forEach(a -> periodByAcct.put(a.accountId(), a));

        // Unión de cuentas (con apertura y/o movimiento), ordenadas por código.
        Map<Long, AccountAggregate> byAcct = new LinkedHashMap<>(beforeByAcct);
        periodByAcct.forEach(byAcct::putIfAbsent);
        List<AccountAggregate> ordered = new ArrayList<>(byAcct.values());
        ordered.sort((a, b) -> a.code().compareTo(b.code()));

        List<LedgerDtos.TrialRow> rows = new ArrayList<>();
        BigDecimal tOpenDb = BigDecimal.ZERO, tOpenCr = BigDecimal.ZERO;
        BigDecimal tMovDb = BigDecimal.ZERO, tMovCr = BigDecimal.ZERO;
        BigDecimal tCloseDb = BigDecimal.ZERO, tCloseCr = BigDecimal.ZERO;
        for (AccountAggregate a : ordered) {
            AccountAggregate before = beforeByAcct.get(a.accountId());
            AccountAggregate period = periodByAcct.get(a.accountId());
            BigDecimal openingNet = before != null ? nz(before.debit()).subtract(nz(before.credit())) : BigDecimal.ZERO;
            BigDecimal periodDebit = period != null ? nz(period.debit()) : BigDecimal.ZERO;
            BigDecimal periodCredit = period != null ? nz(period.credit()) : BigDecimal.ZERO;
            BigDecimal closingNet = openingNet.add(periodDebit).subtract(periodCredit);

            BigDecimal openDb = openingNet.signum() > 0 ? openingNet : BigDecimal.ZERO;
            BigDecimal openCr = openingNet.signum() < 0 ? openingNet.negate() : BigDecimal.ZERO;
            BigDecimal closeDb = closingNet.signum() > 0 ? closingNet : BigDecimal.ZERO;
            BigDecimal closeCr = closingNet.signum() < 0 ? closingNet.negate() : BigDecimal.ZERO;

            rows.add(new LedgerDtos.TrialRow(a.accountId(), a.code(), a.name(),
                    openDb, openCr, periodDebit, periodCredit, closeDb, closeCr));
            tOpenDb = tOpenDb.add(openDb); tOpenCr = tOpenCr.add(openCr);
            tMovDb = tMovDb.add(periodDebit); tMovCr = tMovCr.add(periodCredit);
            tCloseDb = tCloseDb.add(closeDb); tCloseCr = tCloseCr.add(closeCr);
        }
        LedgerDtos.TrialRow totals = new LedgerDtos.TrialRow(null, null, "TOTALES",
                tOpenDb, tOpenCr, tMovDb, tMovCr, tCloseDb, tCloseCr);
        boolean balanced = tCloseDb.subtract(tCloseCr).abs().compareTo(new BigDecimal("0.01")) < 0;
        return new LedgerDtos.TrialBalance(periodId, rows, totals, balanced);
    }

    /** Mayor (movimientos con saldo corrido) de una cuenta, opcionalmente filtrado por período. */
    @Transactional
    public LedgerDtos.AccountLedger accountLedger(Long accountId, Long periodId) {
        Long companyId = tenant.getCompanyId();
        Account account = accounts.findByIdAndCompanyId(accountId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta " + accountId + " no encontrada"));

        BigDecimal opening = BigDecimal.ZERO;
        if (periodId != null) {
            AccountingPeriod p = periods.findByIdAndCompanyId(periodId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Período " + periodId + " no encontrado"));
            opening = lines.aggregateByAccountBeforeDate(companyId, p.getStartDate()).stream()
                    .filter(a -> a.accountId().equals(accountId))
                    .findFirst()
                    .map(a -> nz(a.debit()).subtract(nz(a.credit())))
                    .orElse(BigDecimal.ZERO);
        }

        BigDecimal running = opening;
        BigDecimal totalDebit = BigDecimal.ZERO, totalCredit = BigDecimal.ZERO;
        List<LedgerDtos.Movement> movements = new ArrayList<>();
        for (LedgerLineRow m : lines.movementsForAccount(companyId, accountId, periodId)) {
            running = running.add(nz(m.debit())).subtract(nz(m.credit()));
            totalDebit = totalDebit.add(nz(m.debit()));
            totalCredit = totalCredit.add(nz(m.credit()));
            movements.add(new LedgerDtos.Movement(m.date(), m.reference(), m.description(),
                    m.debit(), m.credit(), running));
        }
        return new LedgerDtos.AccountLedger(accountId, account.getCode(), account.getName(),
                opening, movements, totalDebit, totalCredit, running);
    }

    private static char firstDigit(String code) {
        return code != null && !code.isEmpty() ? code.charAt(0) : ' ';
    }

    private static BigDecimal sum(List<StatementLine> lines) {
        return lines.stream().map(StatementLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
