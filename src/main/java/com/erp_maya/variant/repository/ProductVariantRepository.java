package com.erp_maya.variant.repository;

import com.erp_maya.variant.domain.ProductVariant;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByCompanyId(Long companyId);

    List<ProductVariant> findByCompanyIdAndProductId(Long companyId, Long productId);

    Optional<ProductVariant> findByIdAndCompanyId(Long id, Long companyId);
}
