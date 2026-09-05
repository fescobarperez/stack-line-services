package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.Account;
import com.erp_maya.accounting.domain.AccountingPeriod;
import com.erp_maya.accounting.domain.JournalEntry;
import com.erp_maya.accounting.domain.JournalEntryLine;
import com.erp_maya.accounting.dto.JournalEntryDtos;
import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.accounting.repository.AccountingPeriodRepository;
import com.erp_maya.accounting.repository.JournalEntryRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

@Singleton
public class JournalEntryService {

    private final JournalEntryRepository entries;
    private final AccountRepository accounts;
    private final AccountingPeriodRepository periods;
    private final TenantContext tenant;

    public JournalEntryService(JournalEntryRepository entries, AccountRepository accounts,
                               AccountingPeriodRepository periods, TenantContext tenant) {
        this.entries = entries;
        this.accounts = accounts;
        this.periods = periods;
        this.tenant = tenant;
    }

    @Transactional
    public Page<JournalEntryDtos.Response> list(Pageable pageable) {
        return entries.findByCompanyIdOrderByEntryDateDesc(tenant.getCompanyId(), pageable)
                .map(JournalEntryService::toResponse);
    }

    @Transactional
    public JournalEntryDtos.Response get(Long id) {
        return toResponse(entries.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Póliza " + id + " no encontrada")));
    }

    @Transactional
    public JournalEntryDtos.Response create(JournalEntryDtos.Request req) {
        Long companyId = tenant.getCompanyId();

        JournalEntry entry = new JournalEntry();
        entry.setCompanyId(companyId);
        entry.setEntryDate(req.entryDate());
        entry.setEntryType(req.entryType() != null ? req.entryType() : "manual");
        entry.setDescription(req.description());
        entry.setReference(req.reference());
        entry.setSourceType(req.sourceType());

        if (req.periodId() != null) {
            AccountingPeriod period = periods.findByIdAndCompanyId(req.periodId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Período " + req.periodId() + " no encontrado"));
            if ("closed".equals(period.getStatus())) {
                throw new IllegalStateException("El período contable está cerrado; no admite nuevas pólizas.");
            }
            entry.setPeriod(period);
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (JournalEntryDtos.LineRequest lr : req.lines()) {
            Account account = accounts.findByIdAndCompanyId(lr.accountId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cuenta " + lr.accountId() + " no encontrada"));
            BigDecimal debit = lr.debit() != null ? lr.debit() : BigDecimal.ZERO;
            BigDecimal credit = lr.credit() != null ? lr.credit() : BigDecimal.ZERO;

            JournalEntryLine line = new JournalEntryLine();
            line.setAccount(account);
            line.setDebit(debit);
            line.setCredit(credit);
            entry.addLine(line);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalStateException("La póliza no cuadra: debe (" + totalDebit
                    + ") != haber (" + totalCredit + ").");
        }

        entry.setTotalDebit(totalDebit);
        entry.setTotalCredit(totalCredit);
        entry.setStatus("posted");
        return toResponse(entries.save(entry));
    }

    private static JournalEntryDtos.Response toResponse(JournalEntry e) {
        var lines = e.getLines().stream().map(l -> new JournalEntryDtos.LineResponse(
                l.getId(), l.getAccount() != null ? l.getAccount().getId() : null,
                l.getAccount() != null ? l.getAccount().getCode() : null,
                l.getAccount() != null ? l.getAccount().getName() : null,
                l.getDebit(), l.getCredit())).toList();
        return new JournalEntryDtos.Response(e.getId(),
                e.getPeriod() != null ? e.getPeriod().getId() : null,
                e.getEntryDate(), e.getEntryType(), e.getDescription(), e.getReference(),
                e.getSourceType(), e.getTotalDebit(), e.getTotalCredit(), e.getStatus(), lines);
    }
}
