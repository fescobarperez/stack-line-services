package com.erp_maya.partner.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.SupplierWebsite;
import com.erp_maya.partner.dto.SupplierWebsiteDtos;
import com.erp_maya.partner.repository.SupplierRepository;
import com.erp_maya.partner.repository.SupplierWebsiteRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class SupplierWebsiteService {

    private final SupplierWebsiteRepository repository;
    private final SupplierRepository supplierRepository;
    private final TenantContext tenant;

    public SupplierWebsiteService(SupplierWebsiteRepository repository,
                                  SupplierRepository supplierRepository,
                                  TenantContext tenant) {
        this.repository = repository;
        this.supplierRepository = supplierRepository;
        this.tenant = tenant;
    }

    @Transactional
    public List<SupplierWebsiteDtos.Response> list(Long supplierId) {
        Long companyId = tenant.getCompanyId();
        List<SupplierWebsite> rows = supplierId == null
                ? repository.findByCompanyIdOrderByNameAsc(companyId)
                : repository.findByCompanyIdAndSupplierIdOrderByNameAsc(companyId, supplierId);
        return rows.stream().map(SupplierWebsiteService::toResponse).toList();
    }

    @Transactional
    public SupplierWebsiteDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public SupplierWebsiteDtos.Response create(SupplierWebsiteDtos.Request request) {
        ensureSupplier(request.supplierId());
        SupplierWebsite website = new SupplierWebsite();
        website.setCompanyId(tenant.getCompanyId());
        apply(website, request);
        return toResponse(repository.save(website));
    }

    @Transactional
    public SupplierWebsiteDtos.Response update(Long id, SupplierWebsiteDtos.Request request) {
        ensureSupplier(request.supplierId());
        SupplierWebsite website = find(id);
        apply(website, request);
        return toResponse(repository.update(website));
    }

    private void apply(SupplierWebsite website, SupplierWebsiteDtos.Request request) {
        website.setSupplierId(request.supplierId());
        website.setName(request.name().trim());
        website.setBaseUrl(request.baseUrl().trim());
        website.setWebsiteType(defaultValue(request.websiteType(), "catalog"));
        website.setStatus(defaultValue(request.status(), "active"));
        website.setCrawlEnabled(request.crawlEnabled() == null || request.crawlEnabled());
    }

    private void ensureSupplier(Long supplierId) {
        supplierRepository.findByIdAndCompanyId(supplierId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + supplierId + " no encontrado"));
    }

    private SupplierWebsite find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Sitio del proveedor " + id + " no encontrado"));
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static SupplierWebsiteDtos.Response toResponse(SupplierWebsite website) {
        return new SupplierWebsiteDtos.Response(website.getId(), website.getSupplierId(), website.getName(),
                website.getBaseUrl(), website.getWebsiteType(), website.getStatus(), website.getCrawlEnabled(),
                website.getCreatedAt(), website.getUpdatedAt());
    }
}
