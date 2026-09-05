package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.Account;
import com.erp_maya.accounting.dto.AccountDtos;
import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class AccountService {

    private final AccountRepository repository;
    private final TenantContext tenant;

    public AccountService(AccountRepository repository, TenantContext tenant) {
        this.repository = repository;
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
        apply(a, req);
        return toResponse(repository.update(a));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private void apply(Account a, AccountDtos.Request req) {
        a.setCode(req.code());
        a.setName(req.name());
        a.setLevel(req.level());
        a.setNormalBalance(req.normalBalance());
        a.setAllowsEntries(req.allowsEntries() != null ? req.allowsEntries() : Boolean.TRUE);
        a.setParent(resolveParent(req.parentId()));
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
