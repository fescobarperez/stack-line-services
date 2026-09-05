package com.erp_maya.company.service;

import com.erp_maya.company.domain.Establishment;
import com.erp_maya.company.dto.EstablishmentDtos;
import com.erp_maya.company.repository.EstablishmentRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class EstablishmentService {

    private final EstablishmentRepository repository;
    private final TenantContext tenant;

    public EstablishmentService(EstablishmentRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public List<EstablishmentDtos.Response> list() {
        return repository.findByCompanyId(tenant.getCompanyId())
                .stream().map(EstablishmentService::toResponse).toList();
    }

    @Transactional
    public EstablishmentDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public EstablishmentDtos.Response create(EstablishmentDtos.Request req) {
        Establishment e = new Establishment();
        e.setCompanyId(tenant.getCompanyId());
        apply(e, req);
        return toResponse(repository.save(e));
    }

    @Transactional
    public EstablishmentDtos.Response update(Long id, EstablishmentDtos.Request req) {
        Establishment e = find(id);
        apply(e, req);
        return toResponse(repository.update(e));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private void apply(Establishment e, EstablishmentDtos.Request req) {
        e.setSatCode(req.satCode());
        e.setCommercialName(req.commercialName());
        e.setAddress(req.address());
        e.setPhone(req.phone());
        e.setStatus(req.status() != null ? req.status() : "active");
    }

    private Establishment find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento " + id + " no encontrado"));
    }

    private static EstablishmentDtos.Response toResponse(Establishment e) {
        return new EstablishmentDtos.Response(e.getId(), e.getSatCode(), e.getCommercialName(),
                e.getAddress(), e.getPhone(), e.getStatus());
    }
}
