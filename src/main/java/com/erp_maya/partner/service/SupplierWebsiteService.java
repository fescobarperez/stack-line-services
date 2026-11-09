package com.erp_maya.partner.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.SupplierWebsite;
import com.erp_maya.partner.dto.SupplierWebsiteDtos;
import com.erp_maya.partner.repository.SupplierRepository;
import com.erp_maya.partner.repository.SupplierWebsiteRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        boolean duplicate = repository
                .findByCompanyIdAndSupplierIdOrderByNameAsc(tenant.getCompanyId(), request.supplierId())
                .stream()
                .filter(existing -> !Objects.equals(existing.getId(), website.getId()))
                .anyMatch(existing -> comparableBaseUrl(existing.getBaseUrl()).equals(baseUrl));
        if (duplicate) {
            throw new IllegalStateException("Este proveedor ya tiene registrada esa página web");
        }

        website.setSupplierId(request.supplierId());
        website.setName(request.name().trim());
        website.setBaseUrl(baseUrl);
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

    static String normalizeBaseUrl(String value) {
        try {
            URI parsed = new URI(value.trim());
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || parsed.getHost() == null || parsed.getHost().isBlank()
                    || parsed.getUserInfo() != null) {
                throw invalidUrl();
            }

            int port = parsed.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            String path = parsed.getRawPath() == null ? "" : parsed.getRawPath();
            while (path.endsWith("/") && !path.isEmpty()) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(scheme, null, parsed.getHost().toLowerCase(Locale.ROOT), port,
                    path, parsed.getRawQuery(), null).toASCIIString();
        } catch (URISyntaxException | NullPointerException | IllegalArgumentException exception) {
            throw invalidUrl();
        }
    }

    private static String comparableBaseUrl(String value) {
        try {
            return normalizeBaseUrl(value);
        } catch (IllegalStateException exception) {
            return value == null ? "" : value.trim();
        }
    }

    private static IllegalStateException invalidUrl() {
        return new IllegalStateException("La URL debe empezar con http:// o https:// y contener un dominio válido");
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
