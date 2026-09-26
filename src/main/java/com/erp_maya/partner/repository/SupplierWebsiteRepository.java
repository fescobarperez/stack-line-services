package com.erp_maya.partner.repository;

import com.erp_maya.partner.domain.SupplierWebsite;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierWebsiteRepository extends JpaRepository<SupplierWebsite, Long> {

    List<SupplierWebsite> findByCompanyIdOrderByNameAsc(Long companyId);

    List<SupplierWebsite> findByCompanyIdAndSupplierIdOrderByNameAsc(Long companyId, Long supplierId);

    Optional<SupplierWebsite> findByIdAndCompanyId(Long id, Long companyId);
}
