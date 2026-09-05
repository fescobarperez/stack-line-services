package com.erp_maya.company.service;

import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.domain.Establishment;
import com.erp_maya.company.dto.BranchDtos;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.company.repository.EstablishmentRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class BranchService {

    private final BranchRepository branches;
    private final EstablishmentRepository establishments;
    private final TenantContext tenant;

    public BranchService(BranchRepository branches, EstablishmentRepository establishments, TenantContext tenant) {
        this.branches = branches;
        this.establishments = establishments;
        this.tenant = tenant;
    }

    @Transactional
    public List<BranchDtos.Response> list() {
        return branches.findByCompanyId(tenant.getCompanyId())
                .stream().map(BranchService::toResponse).toList();
    }

    @Transactional
    public BranchDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public BranchDtos.Response create(BranchDtos.Request req) {
        Branch b = new Branch();
        b.setCompanyId(tenant.getCompanyId());
        apply(b, req);
        return toResponse(branches.save(b));
    }

    @Transactional
    public BranchDtos.Response update(Long id, BranchDtos.Request req) {
        Branch b = find(id);
        apply(b, req);
        return toResponse(branches.update(b));
    }

    @Transactional
    public void delete(Long id) {
        branches.delete(find(id));
    }

    private void apply(Branch b, BranchDtos.Request req) {
        b.setName(req.name());
        b.setAddress(req.address());
        b.setStatus(req.status() != null ? req.status() : "active");
        b.setEstablishment(resolveEstablishment(req.establishmentId()));
    }

    private Establishment resolveEstablishment(Long establishmentId) {
        if (establishmentId == null) {
            return null;
        }
        return establishments.findByIdAndCompanyId(establishmentId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento " + establishmentId + " no encontrado"));
    }

    private Branch find(Long id) {
        return branches.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal " + id + " no encontrada"));
    }

    private static BranchDtos.Response toResponse(Branch b) {
        Establishment e = b.getEstablishment();
        return new BranchDtos.Response(b.getId(), b.getName(),
                e != null ? e.getId() : null,
                e != null ? e.getCommercialName() : null,
                b.getAddress(), b.getStatus());
    }
}
