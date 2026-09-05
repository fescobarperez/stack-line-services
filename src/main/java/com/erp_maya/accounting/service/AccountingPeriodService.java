package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.AccountingPeriod;
import com.erp_maya.accounting.dto.AccountingPeriodDtos;
import com.erp_maya.accounting.repository.AccountingPeriodRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class AccountingPeriodService {

    private final AccountingPeriodRepository repository;
    private final TenantContext tenant;

    public AccountingPeriodService(AccountingPeriodRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public List<AccountingPeriodDtos.Response> list() {
        return repository.findByCompanyIdOrderByStartDateDesc(tenant.getCompanyId())
                .stream().map(AccountingPeriodService::toResponse).toList();
    }

    @Transactional
    public AccountingPeriodDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public AccountingPeriodDtos.Response create(AccountingPeriodDtos.Request req) {
        AccountingPeriod p = new AccountingPeriod();
        p.setCompanyId(tenant.getCompanyId());
        p.setName(req.name());
        p.setStartDate(req.startDate());
        p.setEndDate(req.endDate());
        p.setStatus(req.status() != null ? req.status() : "open");
        return toResponse(repository.save(p));
    }

    @Transactional
    public AccountingPeriodDtos.Response close(Long id) {
        AccountingPeriod p = find(id);
        p.setStatus("closed");
        return toResponse(repository.update(p));
    }

    private AccountingPeriod find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Período " + id + " no encontrado"));
    }

    private static AccountingPeriodDtos.Response toResponse(AccountingPeriod p) {
        return new AccountingPeriodDtos.Response(p.getId(), p.getName(),
                p.getStartDate(), p.getEndDate(), p.getStatus());
    }
}
