package com.erp_maya.catalog.repository;

import com.erp_maya.catalog.domain.ProductSupplier;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, Long> {

    List<ProductSupplier> findByCompanyIdAndProductIdOrderByPreferredDesc(Long companyId, Long productId);

    Optional<ProductSupplier> findByCompanyIdAndProductIdAndSupplierId(Long companyId, Long productId, Long supplierId);
}
