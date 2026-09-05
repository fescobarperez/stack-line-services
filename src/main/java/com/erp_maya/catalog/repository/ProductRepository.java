package com.erp_maya.catalog.repository;

import com.erp_maya.catalog.domain.Product;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByCompanyId(Long companyId, Pageable pageable);

    Page<Product> findByCompanyIdAndNameContainsIgnoreCase(Long companyId, String name, Pageable pageable);

    Optional<Product> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Product> findBySkuAndCompanyId(String sku, Long companyId);
}
