package com.erp_maya.search.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.search.dto.SearchDtos.ClientHit;
import com.erp_maya.search.dto.SearchDtos.ProductHit;
import com.erp_maya.search.dto.SearchDtos.SearchResponse;
import com.erp_maya.search.repository.SearchClientRepository;
import com.erp_maya.search.repository.SearchProductRepository;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Búsqueda global (⌘K): productos y clientes por nombre/SKU/NIT. */
@Singleton
public class SearchService {

    private static final int LIMIT = 5;

    private final SearchProductRepository products;
    private final SearchClientRepository clients;
    private final TenantContext tenant;

    public SearchService(SearchProductRepository products, SearchClientRepository clients, TenantContext tenant) {
        this.products = products;
        this.clients = clients;
        this.tenant = tenant;
    }

    @Transactional
    public SearchResponse search(String q) {
        if (q == null || q.isBlank()) {
            return new SearchResponse(List.of(), List.of());
        }
        Long companyId = tenant.getCompanyId();
        String term = "%" + q.trim().toLowerCase() + "%";
        Pageable page = Pageable.from(0, LIMIT);

        List<ProductHit> productHits = products.search(companyId, term, page).stream()
                .map(r -> new ProductHit((Long) r[0], (String) r[1], (String) r[2], (BigDecimal) r[3]))
                .toList();

        List<ClientHit> clientHits = clients.search(companyId, term, page).stream()
                .map(r -> new ClientHit((Long) r[0], (String) r[1], (String) r[2]))
                .toList();

        return new SearchResponse(productHits, clientHits);
    }
}
