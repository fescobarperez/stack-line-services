package com.erp_maya.partner.service;

import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.domain.ClientBalance;
import com.erp_maya.partner.repository.ClientBalanceRepository;
import com.erp_maya.partner.dto.ClientDtos;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class ClientService {

    private final ClientRepository repository;
    private final ClientBalanceRepository balances;
    private final TenantContext tenant;

    public ClientService(ClientRepository repository, ClientBalanceRepository balances,
                         TenantContext tenant) {
        this.repository = repository;
        this.balances = balances;
        this.tenant = tenant;
    }

    @Transactional
    public Page<ClientDtos.Response> list(String search, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<Client> page = (search == null || search.isBlank())
                ? repository.findByCompanyId(companyId, pageable)
                : repository.findByCompanyIdAndNameContainsIgnoreCase(companyId, search.trim(), pageable);
        Map<Long, BigDecimal> saldos = balanceMap(companyId);
        return page.map(c -> toResponse(c, saldos.get(c.getId())));
    }

    /** Busca por NIT. Devuelve vacío si no hay: el llamador decide si lo crea. */
    @Transactional
    public java.util.Optional<ClientDtos.Response> findByNit(String nit) {
        if (nit == null || nit.isBlank()) return java.util.Optional.empty();
        return repository.findByCompanyIdAndNit(tenant.getCompanyId(), nit.trim())
                .map(this::withBalance);
    }

    @Transactional
    public ClientDtos.Response get(Long id) {
        return withBalance(find(id));
    }

    @Transactional
    public ClientDtos.Response create(ClientDtos.Request req) {
        Client c = new Client();
        c.setCompanyId(tenant.getCompanyId());
        apply(c, req);
        return withBalance(repository.save(c));
    }

    @Transactional
    public ClientDtos.Response update(Long id, ClientDtos.Request req) {
        Client c = find(id);
        apply(c, req);
        return withBalance(repository.update(c));
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
        c.setOpeningBalance(req.openingBalance() != null ? req.openingBalance() : BigDecimal.ZERO);
        c.setStatus(req.status() != null ? req.status() : "active");
    }

    private Client find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente " + id + " no encontrado"));
    }

    /**
     * El saldo no vive en el cliente: se deriva de ventas a crédito y abonos
     * (ver la 046). Se pasa aparte para no obligar a una consulta por fila.
     */
    private static ClientDtos.Response toResponse(Client c, BigDecimal balance) {
        return new ClientDtos.Response(c.getId(), c.getName(), c.getNit(), c.getClientType(),
                c.getAddress(), c.getPhone(), c.getEmail(), c.getCreditLimit(),
                c.getPaymentTerms(), balance != null ? balance : BigDecimal.ZERO,
                c.getOpeningBalance(), c.getStatus());
    }

    private ClientDtos.Response withBalance(Client c) {
        return toResponse(c, balances.findById(c.getId())
                .map(ClientBalance::getBalance).orElse(BigDecimal.ZERO));
    }

    private Map<Long, BigDecimal> balanceMap(Long companyId) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (ClientBalance b : balances.findByCompanyId(companyId)) {
            m.put(b.getClientId(), b.getBalance());
        }
        return m;
    }
}
