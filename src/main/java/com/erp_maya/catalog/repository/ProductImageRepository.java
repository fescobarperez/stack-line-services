package com.erp_maya.catalog.repository;

import com.erp_maya.catalog.domain.ProductImage;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    Optional<ProductImage> findByProductIdAndCompanyId(Long productId, Long companyId);
}
