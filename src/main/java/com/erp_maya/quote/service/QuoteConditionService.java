package com.erp_maya.quote.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.quote.domain.QuoteCondition;
import com.erp_maya.quote.dto.QuoteConditionDtos;
import com.erp_maya.quote.repository.QuoteConditionRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

/** Condiciones comerciales de la empresa: se imprimen, en orden, en cada cotización. */
@Singleton
public class QuoteConditionService {

    private final QuoteConditionRepository repository;
    private final TenantContext tenant;

    public QuoteConditionService(QuoteConditionRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public List<QuoteConditionDtos.Response> list() {
        return repository.listarPorEmpresa(tenant.getCompanyId())
                .stream().map(QuoteConditionService::toResponse).toList();
    }

    /** Las que salen en el PDF. */
    @Transactional
    public List<QuoteCondition> activas(Long companyId) {
        return repository.listarActivas(companyId);
    }

    @Transactional
    public QuoteConditionDtos.Response create(QuoteConditionDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        QuoteCondition c = new QuoteCondition();
        c.setCompanyId(companyId);
        apply(c, req);
        if (req.sortOrder() == null) {
            // Al final de la lista.
            c.setSortOrder(repository.listarPorEmpresa(companyId).stream()
                    .mapToInt(QuoteCondition::getSortOrder).max().orElse(0) + 1);
        }
        return toResponse(repository.save(c));
    }

    @Transactional
    public QuoteConditionDtos.Response update(Long id, QuoteConditionDtos.Request req) {
        QuoteCondition c = find(id);
        apply(c, req);
        return toResponse(repository.update(c));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private void apply(QuoteCondition c, QuoteConditionDtos.Request req) {
        c.setTitle(req.title().trim());
        c.setBody(req.body().trim());
        if (req.sortOrder() != null) c.setSortOrder(req.sortOrder());
        c.setActive(req.active() == null || req.active());
    }

    private QuoteCondition find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Condición " + id + " no encontrada"));
    }

    private static QuoteConditionDtos.Response toResponse(QuoteCondition c) {
        return new QuoteConditionDtos.Response(c.getId(), c.getTitle(), c.getBody(),
                c.getSortOrder() == null ? 0 : c.getSortOrder(), !Boolean.FALSE.equals(c.getActive()));
    }
}
