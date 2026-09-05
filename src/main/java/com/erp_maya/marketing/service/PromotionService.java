package com.erp_maya.marketing.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.marketing.domain.Promotion;
import com.erp_maya.marketing.domain.PromotionUsage;
import com.erp_maya.marketing.dto.PromotionDtos;
import com.erp_maya.marketing.repository.PromotionRepository;
import com.erp_maya.marketing.repository.PromotionUsageRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class PromotionService {

    private final PromotionRepository repository;
    private final PromotionUsageRepository usages;
    private final TenantContext tenant;

    public PromotionService(PromotionRepository repository, PromotionUsageRepository usages, TenantContext tenant) {
        this.repository = repository;
        this.usages = usages;
        this.tenant = tenant;
    }

    @Transactional
    public List<PromotionDtos.Response> list() {
        Long companyId = tenant.getCompanyId();
        Map<Long, long[]> agg = new HashMap<>();     // promoId → [uses, tickets]
        Map<Long, BigDecimal> saved = new HashMap<>();
        for (Object[] r : usages.aggregateByPromotion(companyId)) {
            Long pid = ((Number) r[0]).longValue();
            agg.put(pid, new long[]{ ((Number) r[1]).longValue(), ((Number) r[3]).longValue() });
            saved.put(pid, (BigDecimal) r[2]);
        }
        return repository.findByCompanyId(companyId).stream()
                .map(p -> toResponse(p, agg.get(p.getId()), saved.get(p.getId()))).toList();
    }

    @Transactional
    public PromotionDtos.Response get(Long id) {
        Promotion p = find(id);
        return toResponse(p, metricsFor(p.getId()), savedFor(p.getId()));
    }

    @Transactional
    public PromotionDtos.Response create(PromotionDtos.Request req) {
        Promotion p = new Promotion();
        p.setCompanyId(tenant.getCompanyId());
        apply(p, req);
        return toResponse(repository.save(p), null, null);
    }

    @Transactional
    public PromotionDtos.Response update(Long id, PromotionDtos.Request req) {
        Promotion p = find(id);
        apply(p, req);
        return toResponse(repository.update(p), metricsFor(id), savedFor(id));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    /** Registra una aplicación de la promoción (tracking desde POS u otro origen). */
    @Transactional
    public PromotionDtos.Response registerUsage(Long id, PromotionDtos.UsageRequest req) {
        Promotion p = find(id);
        usages.save(new PromotionUsage(tenant.getCompanyId(), p.getId(),
                req.saleId(), req.amountSaved(), req.reference()));
        return toResponse(p, metricsFor(id), savedFor(id));
    }

    private long[] metricsFor(Long promoId) {
        for (Object[] r : usages.aggregateByPromotion(tenant.getCompanyId())) {
            if (((Number) r[0]).longValue() == promoId) {
                return new long[]{ ((Number) r[1]).longValue(), ((Number) r[3]).longValue() };
            }
        }
        return null;
    }

    private BigDecimal savedFor(Long promoId) {
        for (Object[] r : usages.aggregateByPromotion(tenant.getCompanyId())) {
            if (((Number) r[0]).longValue() == promoId) return (BigDecimal) r[2];
        }
        return null;
    }

    private void apply(Promotion p, PromotionDtos.Request req) {
        p.setName(req.name());
        p.setPromoType(req.promoType());
        p.setStatus(req.status() != null ? req.status() : "active");
        p.setValue(req.value());
        p.setCategory(req.category());
        p.setProduct(req.product());
        p.setClientType(req.clientType());
        p.setBranches(req.branches());
        p.setDays(req.days());
        p.setHoraInicio(req.horaInicio());
        p.setHoraFin(req.horaFin());
        p.setMinCompra(req.minCompra());
        p.setNxmN(req.nxmN());
        p.setNxmM(req.nxmM());
        p.setDateStart(req.dateStart());
        p.setDateEnd(req.dateEnd());
        p.setDescription(req.description());
    }

    private Promotion find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Promoción " + id + " no encontrada"));
    }

    private static PromotionDtos.Response toResponse(Promotion p, long[] metrics, BigDecimal savings) {
        int uses = metrics != null ? (int) metrics[0] : 0;
        int tickets = metrics != null ? (int) metrics[1] : 0;
        return new PromotionDtos.Response(p.getId(), p.getName(), p.getPromoType(), p.getStatus(),
                p.getValue(), p.getCategory(), p.getProduct(), p.getClientType(), p.getBranches(),
                p.getDays(), p.getHoraInicio(), p.getHoraFin(), p.getMinCompra(),
                p.getNxmN(), p.getNxmM(), p.getDateStart(), p.getDateEnd(), p.getDescription(),
                uses, savings != null ? savings : BigDecimal.ZERO, tickets);
    }
}
