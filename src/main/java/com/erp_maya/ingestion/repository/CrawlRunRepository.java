package com.erp_maya.ingestion.repository;

import com.erp_maya.ingestion.domain.CrawlRun;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrawlRunRepository extends JpaRepository<CrawlRun, Long> {
    Optional<CrawlRun> findByIdAndCompanyId(Long id, Long companyId);
    Optional<CrawlRun> findFirstByCompanyIdAndSupplierWebsiteIdAndStatusOrderByCreatedAtDesc(Long companyId, Long supplierWebsiteId, String status);
    List<CrawlRun> findByCompanyIdAndSupplierWebsiteIdOrderByCreatedAtDesc(Long companyId, Long supplierWebsiteId);
}
