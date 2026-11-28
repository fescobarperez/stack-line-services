package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.Account;
import com.erp_maya.accounting.dto.AccountDtos;
import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.accounting.repository.JournalEntryLineRepository;
import com.erp_maya.accounting.repository.JournalTemplateLineRepository;
import com.erp_maya.bank.repository.BankAccountRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class AccountService {

    private final AccountRepository repository;
    private final JournalEntryLineRepository lines;
    private final JournalTemplateLineRepository templateLines;
    private final BankAccountRepository bankAccounts;
    private final TenantContext tenant;

    public AccountService(AccountRepository repository, JournalEntryLineRepository lines,
                          JournalTemplateLineRepository templateLines, BankAccountRepository bankAccounts,
                          TenantContext tenant) {
        this.repository = repository;
        this.lines = lines;
        this.templateLines = templateLines;
        this.bankAccounts = bankAccounts;
        this.tenant = tenant;
    }

    @Transactional
    public List<AccountDtos.Response> list() {
        return repository.findByCompanyIdOrderByCode(tenant.getCompanyId())
                .stream().map(AccountService::toResponse).toList();
    }

    @Transactional
    public AccountDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public AccountDtos.Response create(AccountDtos.Request req) {
        Account a = new Account();
        a.setCompanyId(tenant.getCompanyId());
        apply(a, req);
        return toResponse(repository.save(a));
    }

    @Transactional
    public AccountDtos.Response update(Long id, AccountDtos.Request req) {
        Account a = find(id);
        // Cambiarle el nivel a una cuenta con subcuentas dejaría a sus hijas
        // colgando de un nivel que ya no es el de arriba.
        if (req.level() != null && !req.level().equals(a.getLevel()) && repository.existsByParentId(a.getId())) {
            throw new IllegalStateException("La cuenta " + a.getCode()
                    + " tiene subcuentas: no se le puede cambiar el nivel.");
        }
        if (req.parentId() != null && req.parentId().equals(a.getId())) {
            throw new IllegalStateException("Una cuenta no puede ser su propia cuenta padre.");
        }
        apply(a, req);
        return toResponse(repository.update(a));
    }

    /**
     * Se borra solo una cuenta que nadie usa. Con subcuentas, movimientos o
     * plantillas que la apuntan, borrarla rompería el árbol o el libro.
     */
    @Transactional
    public void delete(Long id) {
        Account a = find(id);
        if (repository.existsByParentId(a.getId())) {
            throw new IllegalStateException("La cuenta " + a.getCode() + " tiene subcuentas; elimínalas primero.");
        }
        if (lines.existsByAccountId(a.getId())) {
            throw new IllegalStateException("La cuenta " + a.getCode() + " tiene movimientos; no se puede eliminar.");
        }
        if (templateLines.existsByAccountId(a.getId())) {
            throw new IllegalStateException("La cuenta " + a.getCode()
                    + " se usa en plantillas de partidas; cámbiala ahí antes de eliminarla.");
        }
        if (bankAccounts.existsByGlAccountId(a.getId())) {
            throw new IllegalStateException("La cuenta " + a.getCode()
                    + " está asignada a una cuenta bancaria; quítala ahí antes de eliminarla.");
        }
        repository.delete(a);
    }

    private void apply(Account a, AccountDtos.Request req) {
        Account padre = resolveParent(req.parentId());
        // El árbol tiene que estar completo: nivel N cuelga de uno de nivel N − 1.
        String error = AccountTreeRules.validar(req.level(),
                padre != null ? padre.getCode() : null, padre != null ? padre.getLevel() : null);
        if (error != null) throw new IllegalStateException(error);

        a.setCode(req.code().trim());
        a.setName(req.name().trim());
        a.setLevel(req.level());
        a.setNormalBalance(req.normalBalance());
        a.setAllowsEntries(req.allowsEntries() != null ? req.allowsEntries() : Boolean.TRUE);
        a.setParent(padre);
    }

    private Account resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return repository.findByIdAndCompanyId(parentId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta padre " + parentId + " no encontrada"));
    }

    private Account find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta " + id + " no encontrada"));
    }

    private static AccountDtos.Response toResponse(Account a) {
        Account p = a.getParent();
        return new AccountDtos.Response(a.getId(), a.getCode(), a.getName(),
                p != null ? p.getId() : null, p != null ? p.getCode() : null,
                a.getLevel(), a.getNormalBalance(), a.getAllowsEntries());
    }
}
