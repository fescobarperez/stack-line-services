package com.erp_maya.company.service;

import com.erp_maya.company.domain.Company;
import com.erp_maya.company.dto.CompanyDtos;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

/** Empresa del inquilino actual: solo consulta/actualiza su propio registro. */
@Singleton
public class CompanyService {

    private final CompanyRepository repository;
    private final TenantContext tenant;

    public CompanyService(CompanyRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public CompanyDtos.Response getCurrent() {
        return toResponse(find());
    }

    @Transactional
    public CompanyDtos.Response updateCurrent(CompanyDtos.Request req) {
        Company c = find();
        c.setName(req.name());
        c.setNit(req.nit());
        c.setPlan(req.plan());
        c.setStatus(req.status());
        return toResponse(repository.update(c));
    }

    private Company find() {
        Long id = tenant.getCompanyId();
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa " + id + " no encontrada"));
    }

    private static CompanyDtos.Response toResponse(Company c) {
        return new CompanyDtos.Response(c.getId(), c.getName(), c.getNit(), c.getPlan(), c.getStatus());
    }
}
