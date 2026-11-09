package com.erp_maya.ingestion.repository;

import com.erp_maya.ingestion.domain.CatalogImportItem;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface CatalogImportItemRepository extends JpaRepository<CatalogImportItem, Long> {
    List<CatalogImportItem> findByCompanyIdAndBatchIdOrderByRowNumberAsc(Long companyId, Long batchId);
}
