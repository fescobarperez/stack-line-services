package com.erp_maya.ingestion.repository;

import com.erp_maya.ingestion.domain.SupplierCatalogItem;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface SupplierCatalogItemRepository extends JpaRepository<SupplierCatalogItem, Long> {
    List<SupplierCatalogItem> findByCompanyIdAndProductIdOrderBySupplierId(Long companyId, Long productId);
    List<SupplierCatalogItem> findByCompanyIdAndSupplierIdOrderBySourceProductName(Long companyId, Long supplierId);
}
