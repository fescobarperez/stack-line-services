package com.erp_maya.partner.service;

import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.dto.ClientDtos;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

@Singleton
public class ClientService {

    private final ClientRepository repository;
    private final TenantContext tenant;

    public ClientService(ClientRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public Page<ClientDtos.Response> list(String search, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<Client> page = (search == null || search.isBlank())
                ? repository.findByCompanyId(companyId, pageable)
                : repository.findByCompanyIdAndNameContainsIgnoreCase(companyId, search.trim(), pageable);
        return page.map(ClientService::toResponse);
    }

    @Transactional
    public ClientDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ClientDtos.Response create(ClientDtos.Request req) {
        Client c = new Client();
        c.setCompanyId(tenant.getCompanyId());
        apply(c, req);
        return toResponse(repository.save(c));
    }

    @Transactional
    public ClientDtos.Response update(Long id, ClientDtos.Request req) {
        Client c = find(id);
        apply(c, req);
        return toResponse(repository.update(c));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private void apply(Client c, ClientDtos.Request req) {
        c.setName(req.name());
        c.setNit(req.nit());
        c.setClientType(req.clientType() != null ? req.clientType() : "CF");
        c.setAddress(req.address());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setCreditLimit(req.creditLimit() != null ? req.creditLimit() : BigDecimal.ZERO);
        c.setPaymentTerms(req.paymentTerms() != null ? req.paymentTerms() : 0);
        c.setBalance(req.balance() != null ? req.balance() : BigDecimal.ZERO);
        c.setStatus(req.status() != null ? req.status() : "active");
    }

    private Client find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente " + id + " no encontrado"));
    }

    private static ClientDtos.Response toResponse(Client c) {
        return new ClientDtos.Response(c.getId(), c.getName(), c.getNit(), c.getClientType(),
                c.getAddress(), c.getPhone(), c.getEmail(), c.getCreditLimit(),
                c.getPaymentTerms(), c.getBalance(), c.getStatus());
    }
}
