package com.erp_maya.bank.service;

import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.bank.domain.BankAccount;
import com.erp_maya.bank.domain.BankMovement;
import com.erp_maya.bank.domain.BankReconciliation;
import com.erp_maya.bank.dto.BankDtos;
import com.erp_maya.bank.repository.BankAccountRepository;
import com.erp_maya.bank.repository.BankMovementRepository;
import com.erp_maya.bank.repository.BankReconciliationRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Cuentas bancarias, sus movimientos y conciliaciones. */
@Singleton
public class BankService {

    private final BankAccountRepository accounts;
    private final BankMovementRepository movements;
    private final BankReconciliationRepository reconciliations;
    private final AccountRepository glAccounts;
    private final TenantContext tenant;

    public BankService(BankAccountRepository accounts, BankMovementRepository movements,
                       BankReconciliationRepository reconciliations, AccountRepository glAccounts,
                       TenantContext tenant) {
        this.accounts = accounts;
        this.movements = movements;
        this.reconciliations = reconciliations;
        this.glAccounts = glAccounts;
        this.tenant = tenant;
    }

    // ── Cuentas ───────────────────────────────────────────────────────────
    @Transactional
    public List<BankDtos.AccountResponse> list() {
        return accounts.findByCompanyId(tenant.getCompanyId()).stream().map(BankService::toAccount).toList();
    }

    @Transactional
    public BankDtos.AccountResponse get(Long id) {
        return toAccount(find(id));
    }

    @Transactional
    public BankDtos.AccountResponse create(BankDtos.AccountRequest req) {
        BankAccount a = new BankAccount();
        a.setCompanyId(tenant.getCompanyId());
        apply(a, req);
        a.setBookBalance(a.getBalance());
        return toAccount(accounts.save(a));
    }

    @Transactional
    public BankDtos.AccountResponse update(Long id, BankDtos.AccountRequest req) {
        BankAccount a = find(id);
        apply(a, req);
        return toAccount(accounts.update(a));
    }

    // ── Movimientos ───────────────────────────────────────────────────────
    @Transactional
    public List<BankDtos.MovementResponse> listMovements(Long accountId) {
        find(accountId);
        return movements.findByCompanyIdAndBankAccountIdOrderByMovementDateDesc(tenant.getCompanyId(), accountId)
                .stream().map(BankService::toMovement).toList();
    }

    @Transactional
    public BankDtos.MovementResponse addMovement(Long accountId, BankDtos.MovementRequest req) {
        BankAccount account = find(accountId);
        BigDecimal newBalance = account.getBalance().add(req.amount());

        BankMovement m = new BankMovement();
        m.setCompanyId(tenant.getCompanyId());
        m.setBankAccount(account);
        m.setAmount(req.amount());
        m.setMovementType(req.movementType());
        m.setDescription(req.description());
        m.setReference(req.reference());
        m.setMovementDate(req.movementDate() != null ? req.movementDate() : LocalDate.now());
        m.setRunningBalance(newBalance);
        m.setReconciled(Boolean.FALSE);
        BankMovement saved = movements.save(m);

        account.setBalance(newBalance);
        account.setLastMovementDate(m.getMovementDate());
        return toMovement(saved);
    }

    // ── Conciliaciones ────────────────────────────────────────────────────
    @Transactional
    public List<BankDtos.ReconcileResponse> listReconciliations(Long accountId) {
        find(accountId);
        return reconciliations.findByCompanyIdAndBankAccountIdOrderByStatementDateDesc(tenant.getCompanyId(), accountId)
                .stream().map(BankService::toReconcile).toList();
    }

    @Transactional
    public BankDtos.ReconcileResponse reconcile(Long accountId, BankDtos.ReconcileRequest req) {
        BankAccount account = find(accountId);
        BigDecimal book = account.getBookBalance();
        BigDecimal difference = req.bankBalance().subtract(book);

        BankReconciliation r = new BankReconciliation();
        r.setCompanyId(tenant.getCompanyId());
        r.setBankAccount(account);
        r.setStatementDate(req.statementDate());
        r.setBookBalance(book);
        r.setBankBalance(req.bankBalance());
        r.setDifference(difference);
        r.setNotes(req.notes());
        r.setStatus(difference.compareTo(BigDecimal.ZERO) == 0 ? "reconciled" : "open");
        return toReconcile(reconciliations.save(r));
    }

    private void apply(BankAccount a, BankDtos.AccountRequest req) {
        a.setAccountCode(req.accountCode());
        a.setBankName(req.bankName());
        a.setAccountType(req.accountType());
        a.setCurrency(req.currency() != null ? req.currency() : "GTQ");
        a.setAccountNumber(req.accountNumber());
        a.setAlias(req.alias());
        a.setBalance(req.balance() != null ? req.balance() : BigDecimal.ZERO);
        a.setStatus(req.status() != null ? req.status() : "active");
        a.setOpenedDate(req.openedDate());
        a.setGlAccount(req.glAccountId() == null ? null
                : glAccounts.findByIdAndCompanyId(req.glAccountId(), tenant.getCompanyId()).orElse(null));
    }

    private BankAccount find(Long id) {
        return accounts.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta bancaria " + id + " no encontrada"));
    }

    private static BankDtos.AccountResponse toAccount(BankAccount a) {
        return new BankDtos.AccountResponse(a.getId(), a.getAccountCode(), a.getBankName(), a.getAccountType(),
                a.getCurrency(), a.getAccountNumber(), a.getAlias(), a.getBalance(), a.getBookBalance(),
                a.getStatus(), a.getOpenedDate(), a.getLastMovementDate(),
                a.getGlAccount() != null ? a.getGlAccount().getId() : null);
    }

    private static BankDtos.MovementResponse toMovement(BankMovement m) {
        return new BankDtos.MovementResponse(m.getId(),
                m.getBankAccount() != null ? m.getBankAccount().getId() : null,
                m.getMovementDate(), m.getDescription(), m.getReference(), m.getMovementType(),
                m.getAmount(), m.getRunningBalance(), m.getReconciled());
    }

    private static BankDtos.ReconcileResponse toReconcile(BankReconciliation r) {
        return new BankDtos.ReconcileResponse(r.getId(),
                r.getBankAccount() != null ? r.getBankAccount().getId() : null,
                r.getStatementDate(), r.getBookBalance(), r.getBankBalance(), r.getDifference(),
                r.getStatus(), r.getNotes());
    }
}
