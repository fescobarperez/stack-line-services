package com.erp_maya.ingestion.repository;

import com.erp_maya.ingestion.domain.CatalogImportBatch;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogImportBatchRepository extends JpaRepository<CatalogImportBatch, Long> {
    List<CatalogImportBatch> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<CatalogImportBatch> findByCompanyIdAndSupplierWebsiteIdAndSourceTypeAndStatusOrderByCreatedAtDesc(
            Long companyId, Long supplierWebsiteId, String sourceType, String status);
    Optional<CatalogImportBatch> findFirstByCompanyIdAndSupplierWebsiteIdAndSourceTypeAndStatusOrderByCreatedAtDesc(
            Long companyId, Long supplierWebsiteId, String sourceType, String status);
    Optional<CatalogImportBatch> findFirstByCompanyIdAndCrawlRunId(Long companyId, Long crawlRunId);
    boolean existsByCompanyIdAndSupplierWebsiteIdAndSourceTypeAndSourceReferenceAndStatus(
            Long companyId, Long supplierWebsiteId, String sourceType, String sourceReference, String status);
    Optional<CatalogImportBatch> findByIdAndCompanyId(Long id, Long companyId);
}
