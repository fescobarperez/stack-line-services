package com.erp_maya.partner.repository;

import com.erp_maya.partner.domain.Supplier;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Page<Supplier> findByCompanyId(Long companyId, Pageable pageable);

    Page<Supplier> findByCompanyIdAndNameContainsIgnoreCase(Long companyId, String name, Pageable pageable);

    Optional<Supplier> findByIdAndCompanyId(Long id, Long companyId);
}
