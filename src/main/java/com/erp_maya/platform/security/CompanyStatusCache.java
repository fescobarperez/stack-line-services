package com.erp_maya.platform.security;

import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.platform.service.CompanyRules;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de cada empresa con caché corto, para revisarlo en cada request sin
 * ir a la base cada vez. Al cambiar el estado desde la consola se invalida en
 * el acto; los demás nodos lo ven a más tardar en {@link #TTL}.
 */
@Singleton
public class CompanyStatusCache {

    static final Duration TTL = Duration.ofSeconds(30);

    private record Entry(String status, Instant at) {}

    private final CompanyRepository companies;
    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();

    public CompanyStatusCache(CompanyRepository companies) {
        this.companies = companies;
    }

    public boolean operates(Long companyId) {
        Instant now = Instant.now();
        Entry e = cache.get(companyId);
        if (e == null || now.isAfter(e.at().plus(TTL))) {
            String status = companies.findById(companyId).map(c -> c.getStatus()).orElse(CompanyRules.CANCELLED);
            e = new Entry(status, now);
            cache.put(companyId, e);
        }
        return CompanyRules.operates(e.status());
    }

    public void invalidate(Long companyId) {
        cache.remove(companyId);
    }
}
