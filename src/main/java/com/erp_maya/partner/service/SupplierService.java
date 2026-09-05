package com.erp_maya.partner.service;

import com.erp_maya.partner.domain.Supplier;
import com.erp_maya.partner.dto.SupplierDtos;
import com.erp_maya.partner.repository.SupplierRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class SupplierService {

    private final SupplierRepository repository;
    private final TenantContext tenant;

    public SupplierService(SupplierRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public Page<SupplierDtos.Response> list(String search, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<Supplier> page = (search == null || search.isBlank())
                ? repository.findByCompanyId(companyId, pageable)
                : repository.findByCompanyIdAndNameContainsIgnoreCase(companyId, search.trim(), pageable);
        return page.map(SupplierService::toResponse);
    }

    @Transactional
    public SupplierDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public SupplierDtos.Response create(SupplierDtos.Request req) {
        Supplier s = new Supplier();
        s.setCompanyId(tenant.getCompanyId());
        apply(s, req);
        return toResponse(repository.save(s));
    }

    @Transactional
    public SupplierDtos.Response update(Long id, SupplierDtos.Request req) {
        Supplier s = find(id);
        apply(s, req);
        return toResponse(repository.update(s));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private void apply(Supplier s, SupplierDtos.Request req) {
        s.setName(req.name());
        s.setNit(req.nit());
        s.setContact(req.contact());
        s.setPhone(req.phone());
        s.setPaymentTerms(req.paymentTerms());
        s.setBalance(req.balance() != null ? req.balance() : java.math.BigDecimal.ZERO);
        s.setStatus(req.status() != null ? req.status() : "active");
    }

    private Supplier find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + id + " no encontrado"));
    }

    private static SupplierDtos.Response toResponse(Supplier s) {
        return new SupplierDtos.Response(s.getId(), s.getName(), s.getNit(), s.getContact(),
                s.getPhone(), s.getPaymentTerms(), s.getBalance(), s.getStatus());
    }
}
